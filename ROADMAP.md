# Fork roadmap

Planning document for **this fork only**. Not intended to be sent upstream.

For how the code is laid out and how to run it, see [`CLAUDE.md`](CLAUDE.md).
This file covers what to build and in what order; that one covers what exists.

## Goal

A phone-friendly way to use the character sorter — responsive web UI, and
possibly a native Android app later — that reads and writes the **live database
behind `charsorter.lndyn.com`**, not a local copy.

## The governing constraint

Code can only write that database if it runs somewhere that can reach it, and
the database sits on the upstream maintainer's host (`local_settings.py` has
`HOST: localhost`; it is not exposed).

Three possible shapes:

| Shape | Verdict |
| --- | --- |
| Fork connects to upstream's Postgres remotely | **Avoid.** Requires the maintainer to expose 5432 or grant a tunnel — a bigger security ask than merging a PR. Worse, the fork's migrations would mutate his live schema through a shared `django_migrations` table: one stray `makemigrations` breaks production. |
| Mobile UI merged upstream, owner uses the deployed site | **Best first step.** Smallest change, no infrastructure, no credentials. Cost: every future tweak needs the maintainer to merge. |
| JSON API merged upstream once, fork becomes a pure client | **Best end state.** One focused PR, then the fork iterates independently forever. Required for any native app. |

The last two are complementary; the plan below does both, in order.

### Consequence: no modern Django in anything sent upstream

Anything running on the upstream host must run on **Django 2.0.6 / Python 3.7**.
The modernization path in `CLAUDE.md` ("Path B") still applies to *local
development of this fork*, but no upstream PR may depend on a modern Django.

This costs nothing for the responsive UI — a viewport tag, CSS, and template
structure are version-agnostic. It matters only for the API; see PR 3.

### Consequence: the data-porting problem disappears

Scraping saved HTML, recovering Glicko `(rating, RD)` pairs from `graph.html`,
and adding seed-rating fields to `Character` are all **superseded**. If the fork
reads the live database, the `SortRecord` history is simply there. Do not build
the `import_scraped` command sketched in earlier notes; this architecture
removes the problem it solved.

## PR sequence

Each step is independently useful and independently mergeable. Upstream has been
dormant since 2018, so every PR should be small, additive, and default to
existing behavior — that maximizes the chance of a review.

### PR 1 — Authorization fixes *(implemented; not yet sent upstream)*

Close the POST-body holes documented under "Known issues" in `CLAUDE.md`:

- Scope `undo`'s `SortRecord` lookup to `list_id`.
- Filter the `ModifyCharFormset` queryset on POST (`ModifyCharlistFormset`
  already was).
- Drop the hidden `characterlist` / `owner` fields from the add forms and set
  them in the view instead of trusting the client.
- Look comparison characters up through `charlist.character_set`, replacing an
  `assert` that `python -O` strips.

Pure bug fix, no behavior change for honest users; ~35 lines of source. Also the
lowest-stakes way to find out whether the maintainer is still reachable.

**This is a prerequisite for PR 3.** Through HTML forms these holes are bad;
through a JSON API they are trivially scriptable.

### PR 2 — Responsive web UI

The highest value-per-line change in the plan, and it needs no API.

1. `base.html`: add `<!DOCTYPE html>`, `<meta charset="utf-8">`, and
   `<meta name="viewport" content="width=device-width, initial-scale=1">`.
   The missing viewport tag is the single biggest reason the site is unusable on
   a phone.
2. Add the first stylesheet under `core/static/` and `{% load static %}` it.
   Replace the `<font size="0">` tags in `sort.html` with CSS.
3. Rework the sort page for thumbs. It is currently three radio buttons plus a
   submit button — four taps per comparison. Two large tappable cards plus a
   "Same" button, submitting on tap, makes it one. **This is the core loop;
   optimize it before anything else.**
4. Stack the `edit.html` / `editlists.html` formset tables into cards on narrow
   screens. Mirror any change across both — they share an identical structure.

All Django 2.0-compatible. A PWA manifest plus a service worker is a natural
follow-on and still requires nothing from the server.

### PR 3 — JSON API

Only needed for a native app, or to iterate without waiting on upstream
reviews.

**Do not add Django REST Framework.** Modern DRF requires Django 4.2+, so
upstream would need a pinned 2018 release. This app has six views returning
plain Python data already; hand-rolled `JsonResponse` views add no dependency,
behave identically on Django 2.0 and 5.x, and are far likelier to be merged.

#### The split: two PRs, and the seam is risk

Not size. The endpoints are additive and repetitive — three handlers tell a
reviewer most of what the other five do — and they change nothing that already
runs. The token model is the opposite: it needs a migration against the live
production database and it adds a permanent credential to a site whose README
already concedes it is probably vulnerable. That is the part worth isolating.

| PR | Contents | Source | Tests |
| --- | --- | --- | --- |
| **3a** | The API: module, urlconf, all eight endpoints, session auth | ~270 | ~225 |
| **3b** | `ApiToken` + migration + `POST /api/auth/token` — **deferred** | ~110 | ~100 |

3a is one concern despite the line count: one new module, one new urlconf, and
a single added line in `charactersorter/urls.py`. That last number is the one a
dormant maintainer actually needs, because it answers "can this break what I
already run" without reading the rest. If review balks at the size anyway, the
fallback seam is read-vs-write — every `GET` first, then the mutations — not
one PR per endpoint.

#### Why 3b is deferred

Tokens are speculative work for a goal this document does not commit to.
Session cookies are only "awkward from native code", and native is "a later
choice to re-confirm, not a settled goal" (see below). Meanwhile the token
model carries the plan's only migration against the maintainer's live Postgres,
and an opaque non-expiring bearer key with no rotation and an unthrottled
issuance endpoint is a worse thing to add to this codebase than to most.

Deferring is cheap and reversible: a token becomes one more way to populate
`request.user` alongside the session check in `api_view`, and no endpoint,
payload or ownership rule changes. Shipping a migration to production is not
reversible.

With session auth a client **can** do everything the API offers from the same
origin in a browser — which is exactly the PR 2 PWA. It **cannot** be a native
app, be hosted on another origin (`SameSite=Lax`, and no CORS configuration the
fork can reach), or sync headlessly while logged out.

Revisit if the maintainer signals they would take a migration, if the fork
commits to native, or if the client ends up hosted off-origin.

#### Endpoints as built in 3a

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` `POST` | `/api/lists` | list / create `CharacterList`s |
| `GET` `PATCH` `DELETE` | `/api/lists/<id>` | ranked chars, annotations, progress |
| `GET` `POST` | `/api/lists/<id>/characters` | list / add `Character`s |
| `PATCH` `DELETE` | `/api/lists/<id>/characters/<char_id>` | edit / remove one |
| `GET` | `/api/lists/<id>/next` | next comparison pair (+ image URLs) |
| `POST` | `/api/lists/<id>/comparisons` | `{char1, char2, value, timestamp?}` |
| `DELETE` | `/api/lists/<id>/comparisons/<rec_id>` | undo |
| `GET` | `/api/lists/<id>/graph` | Glicko rating / RD arrays |

Two deviations from the sketch above:

- Characters are nested under their list instead of living at
  `/api/characters/<id>`. A bare character id has no parent in the URL, so it
  would need its own ownership check (`characterlist__owner=request.user`) —
  a second authorization path to keep correct. Nested, every route goes
  through the same `owned_list()` helper.
- `POST /api/auth/token` is deferred with the rest of 3b.

#### Authorization

`owned_list(request, list_id)` is the only gate, and it carries PR 1's
patterns: it resolves the URL's list against `request.user`, and every other id
is then read *through* that list, so a foreign id raises `DoesNotExist` and
becomes a 404 rather than relying on an `assert` that `python -O` strips.
`DELETE /api/lists/<id>/comparisons/<rec_id>` gets its required
`rec.charlist_id == list_id` check structurally, from
`charlist.sortrecord_set.get(id=rec_id)`.

Unlike `requires_list_owner`, there is no superuser bypass — narrower is the
right default for a new surface, and it is one keyword to add back.

`register_comparison` guards its char ids with an `assert`, so the comparison
endpoint resolves `char1`/`char2` through the list itself before calling it.
That holds whether or not PR 1 has merged.

#### Error shape

`{"error": "<sentence>"}`, plus `"fields": {name: [messages]}` when a
`ModelForm` rejected the body. Codes: 400 malformed body or invalid fields,
401 not authenticated, 403 CSRF (Django's own HTML response — status only),
404 every not-found *and* every not-yours, 405 wrong method (empty body, with
an `Allow` header, from `require_http_methods`). 404-for-not-yours is
deliberate: distinguishing them would confirm another user's list exists.
Authentication is checked before the method, so an anonymous caller always
gets the JSON 401 rather than an HTML 405.

#### Client contract

- **`get_next_comparison` is non-deterministic for Glicko.** It samples from a
  softmax, so two `GET /next` calls return different pairs. A client must not
  treat the pending question as re-fetchable; `POST /comparisons` names
  `char1` and `char2` explicitly, so answer the pair you were handed and let
  the next `GET` propose whatever it likes. A client that re-fetches on resume
  simply gets a different, equally valid question.
- **`timestamp` is optional on `POST /comparisons`**, must carry a UTC offset,
  and must not be in the future. It exists for offline queueing:
  `SortRecord.timestamp` is `auto_now_add`, so the endpoint overwrites it
  after `save()`. Backdating is safe because `compute_ratings` replays in
  timestamp order; *forward*-dating is not, and is refused — the Glicko maths
  measures elapsed days from each record, and a negative interval takes the
  square root of a negative, which 500s the ranking, `/next`, `/graph` and the
  HTML pages for that list until wall-clock time catches up. A client with a
  fast clock would otherwise brick a list by accident.
- **`value` must be `-1`, `0` or `1`** — the three the sort page offers, and
  the only three `process_record` can read. It maps `value` to a win/tie/loss
  score, so a large one diverges the ratings until `math.pow` overflows and
  every read path for that list 500s permanently. The HTML view has the same
  gap, but only radio buttons feed it; an API documents the field.
- **A rejected `POST /comparisons` stores nothing.** The body is fully
  validated before the record is created, and the two writes (insert, then the
  timestamp overwrite) share a transaction — so a client that retries after a
  400 does not end up with a duplicate.
- **Writes need `X-CSRFToken`.** The views are not `csrf_exempt`, since they
  authenticate from the session.
- **Every ranking request replays the entire history.** Acceptable for a page
  load; it becomes the hot path when an app polls `/next`. If it gets slow,
  cache on `SortRecord` count + max timestamp — but do **not** reuse the
  per-request `dirty` flag, which is explicitly not valid across requests.
- `/graph` returns real JSON arrays. `get_graph_info` hands back `json.dumps`'d
  strings for the template, so the endpoint parses them once; this also
  side-steps the `graph.html` XSS noted in `CLAUDE.md`.

#### Merge order

PR 1 has merged to this fork's `main`, and 3a rebases onto it cleanly — the
two never conflicted, because 3a adds a module instead of editing
`sorterinput/views.py`, and PR 1 never touches `charactersorter/urls.py`. 3a
also assumes nothing from PR 1: it re-derives every ownership check at its own
call sites, and its tests pass on bare upstream `45a897d` without PR 1 present.

**Nothing has been sent upstream yet.** Every PR so far has been
fork-internal. So 3a is not the right first contact: this document makes PR 1
the cheap signal test of whether the maintainer is reachable at all, and PR 2
the highest value-per-line change. Spending the one likely round of review
attention on the largest and most speculative diff — an API whose own
justification is deferred, since tokens were dropped for want of a confirmed
native client — inverts that. Send PR 1, then PR 2; hold 3a until the
maintainer has answered something.

The upstream branch is ready when that time comes. Per `CONTRIBUTING.md`,
cherry-picking the two source commits onto `45a897d` is clean, leaves only
`charactersorter/` files, and passes the suite under both `python` and
`python -O`.

## Native app vs PWA

A native Android client cannot talk to Postgres directly: that would mean
shipping database credentials in an extractable binary and exposing the port
publicly. Native therefore *requires* PR 3.

Given the app's shape — forms, text lists, one image pair at a time — a
responsive site plus a PWA manifest delivers most of the value for a fraction of
the effort, and PR 2 alone yields a usable phone experience with zero
infrastructure. Treat native as a later choice to re-confirm, not a settled goal.

## Open risks

- **Upstream responsiveness.** No commits since 2018. PRs 1 and 2 leave the fork
  dependent on the maintainer merging every future tweak; PR 3 is what buys
  independence. Worth sending PR 1 early purely as a signal test — a fork that
  cannot reach the database has no path to the goal.
- **Secrets.** `local_settings.py` is tracked despite `.gitignore` and carries a
  `SECRET_KEY` and Postgres password. Rotating them is a prerequisite for any
  real deployment of this fork.
