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
| Mobile UI merged upstream, owner uses the deployed site | **Done** (`#11`). Smallest change, no infrastructure, no credentials. Cost: every future tweak needs the maintainer to merge. |
| JSON API merged upstream once, fork becomes a pure client | **Done** (`#12`). One focused PR, then the fork iterates independently forever. Required for any native app. |

Both landed on 2026-09-02, and both are **deployed** — verified the same day
against the running site. `charsorter.lndyn.com` serves the responsive UI and
all eight `/api/` endpoints, so the fork can now read and write the live
database as a client. That was the whole point of the sequence below.

### Verifying the live site

Merged is not deployed, so this check runs against the running host, never
against upstream `main`. It needs no credentials: `api_view` tests
authentication before anything else, so an anonymous request to a deployed
endpoint returns the JSON 401, while an undeployed path falls through to
Django's HTML 404. The contrast is what makes a 401 evidence of a real route
rather than a catch-all.

    $ curl -s -o /dev/null -w '%{http_code} %{content_type}\n' \
          https://charsorter.lndyn.com/api/lists
    401 application/json

On 2026-09-02 all eight endpoints answered `401 application/json`, while
`/api/bogus` and `/api/auth/token` answered `404 text/html` — the latter
confirming 3b is absent, as intended. The home page carries the viewport tag
and `/static/core/css/style.css`, so `#11` is live too.

A cloud session cannot reach the host under the default **Trusted** network
access: the egress proxy answers `403` to `CONNECT`, and `WebFetch` is bound by
the same policy, so neither is a workaround for the other. Add `*.lndyn.com` to
a **Custom** allowlist on the environment, keeping the default package-manager
list checked, before expecting any of this to run. The change takes effect on a
session that is already running.

Note the host is `charsorter.lndyn.com`. A phone browser elides the leading
`char` in the URL bar, which is a good way to waste a round trip on a name that
does not resolve.

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

Each step is independently useful and independently mergeable. Every PR was
kept small, additive, and defaulting to existing behavior, to maximize the
chance of a review from a maintainer who had not committed since 2019.
**All three merged upstream on 2026-09-02 as `#10`, `#11` and `#12`.**

### PR 1 — Authorization fixes *(merged upstream, `#10`)*

Close the POST-body holes documented under "Known issues" in `CLAUDE.md`:

- Scope `undo`'s `SortRecord` lookup to `list_id`.
- Filter the `ModifyCharFormset` queryset on POST (`ModifyCharlistFormset`
  already was).
- Drop the hidden `characterlist` / `owner` fields from the add forms and set
  them in the view instead of trusting the client.
- Look comparison characters up through `charlist.character_set`, replacing an
  `assert` that `python -O` strips.

Pure bug fix, no behavior change for honest users; ~35 lines of source. Also the
lowest-stakes way to find out whether the maintainer was still reachable —
which is exactly what sending it first established.

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
origin in a browser — which is exactly the PR 2 PWA. It **cannot** be hosted on
another origin in a browser (`SameSite=Lax`, and no CORS configuration the fork
can reach), or sync headlessly while logged out.

An earlier revision of this file also listed "cannot be a native app". That was
wrong, and correcting it is what lets 3b stay deferred. `SameSite` and CORS are
browser enforcement; a native client with its own cookie jar is not a browser.
Verified against the live site on 2026-09-02: `GET /login/` sets a `csrftoken`
cookie (`Secure`, `Path=/`, one-year `Max-Age`) and returns a form carrying
`csrfmiddlewaretoken`. So a native client logs in with a form POST and then
sends the cookie value as `X-CSRFToken` on writes. The one non-obvious extra
requirement is Django's strict Referer check on HTTPS, satisfied by setting
`Referer` to the site's own origin.

**Those triggers are now settled, and none of them fires.** The maintainer
merged three PRs including the API itself, so he is demonstrably reachable —
most of the case for asking. But native does not need a token (above), and a
same-origin client never did. A migration remains a larger ask than code, since
it runs against his live database and the fork cannot test it first. So 3b
stays deferred on evidence rather than on doubt about the maintainer. Revisit
it only for a client that must sync while logged out, which nothing in this
plan requires.

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
- `POST /api/auth/token` is deferred with the rest of 3b. It 404s on the
  deployed site, as it should.

One gap is worth naming, because a client hits it immediately: **there is no
`GET` on `/comparisons`.** A record id comes back only in the `201` from the
client's own `POST`, so a client that restarts cannot undo the last comparison
— the HTML page's Undo button has no API equivalent across process restarts.
A read-only `GET /api/lists/<id>/comparisons` is small, additive and the
natural fourth upstream PR.

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
  authenticate from the session. A write without it is rejected by
  `CsrfViewMiddleware` *before* the view runs, so it returns Django's HTML 403
  page and not the JSON error envelope — confirmed live on 2026-09-02. A client
  that parses every error body as JSON breaks on exactly this case.
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

They went upstream in that order — PR 1 first as the cheap signal test of
whether the maintainer was reachable at all, then PR 2, then 3a once he had
answered — and all three merged. Staggering cost little and kept the largest,
most speculative diff from being his first impression.

## Native app vs PWA

A native Android client cannot talk to Postgres directly: that would mean
shipping database credentials in an extractable binary and exposing the port
publicly. Native therefore *requires* PR 3a — but not 3b; see "Why 3b is
deferred".

Given the app's shape — forms, text lists, one image pair at a time — a
responsive site plus a PWA manifest would deliver most of the value for a
fraction of the effort, and PR 2 alone already yields a usable phone experience
with zero infrastructure.

**Native was chosen anyway, on 2026-09-02, for independence.** A PWA's manifest
and service worker must be served same-origin, so they would live in upstream's
repo and ship on the maintainer's deploy schedule — the exact dependency `#12`
was merged to escape. Native is the only shape the fork can own end to end, and
the auth handshake it needs is verified working (see "Why 3b is deferred").
The cost is accepted: a real toolchain and more code for the same screens.

## The Android client

Scope of the **prototype**: log in, pick a list, answer comparisons, see the
ranking, against the live site on a real phone. Deliberately excluded until it
works — offline queueing, images, the graph, and any list or character editing.

### Module split, and why it is the load-bearing decision

The client lives in `android/` in this fork, as two Gradle modules:

| Module | Contents | Builds with |
| --- | --- | --- |
| `:client` | API client, models, auth handshake. Pure JVM, no Android APIs. | JDK + Gradle alone |
| `:app` | Compose UI, single Activity. | Android SDK |

The split exists because of what a cloud session actually has. Verified
2026-09-02: JDK 21 and Gradle 8.14.3 are pre-installed, but there is **no
Android SDK**, `ANDROID_HOME` is unset, and Google's Maven hosts are not on the
network allowlist. So an APK cannot be built here without adding
`dl.google.com` and `maven.google.com` to the environment's **Custom** list and
installing the SDK from a setup script.

Keeping every risky piece — the login handshake, CSRF, error shapes, the
non-deterministic `/next` — in a pure-JVM module means all of it is testable in
a cloud session today with MockWebServer, and only the UI needs a machine with
Android Studio. Do not let Android types leak into `:client`; that property is
the whole point.

### The auth handshake

Verified against the live site, and the one part worth writing carefully:

1. `GET /login/` — stores the `csrftoken` cookie and returns a form carrying
   `csrfmiddlewaretoken`.
2. `POST /login/` form-encoded with `username`, `password`,
   `csrfmiddlewaretoken`, and a `Referer` of the site's own origin. Success is
   a 302 plus a `sessionid` cookie; a 200 that returns the form again is a
   failed login. Do not follow redirects automatically, or the two are hard to
   tell apart.
3. Every later write sends `X-CSRFToken` from the cookie jar and the same
   `Referer` — Django checks Referer strictly on HTTPS.

An OkHttp `CookieJar` plus one interceptor covers steps 3 for the whole client.
Persist the `sessionid` cookie, never the password, and re-run the handshake on
a 401. Django's default session lifetime is two weeks, so treat re-login as a
normal path rather than an error. The site's README disclaims its own security;
use a password that is not reused anywhere else.

### Error mapping

The API's error shape is not uniform, and a client that assumes JSON breaks:

| Status | Body | Meaning |
| --- | --- | --- |
| 401 | JSON | Not authenticated — re-run the handshake |
| 403 | **HTML** | CSRF token stale — refresh it and retry once |
| 400 | JSON, maybe `fields` | Bad body; surface `fields` per input |
| 404 | HTML or JSON | Not found *or* not yours — do not distinguish |

### Client behavior the API forces

- **Hold the pair from `/next`; never re-fetch it.** Glicko samples from a
  softmax, so a second call returns a different question. Answer what you were
  handed, then fetch the next one.
- **Undo is in-memory only.** The `rec_id` needed for
  `DELETE /comparisons/<id>` arrives only in the `201` from the client's own
  `POST`, so undo works within a run and dies with the process. The durable fix
  is the `GET /comparisons` endpoint noted above — the prototype's only
  upstream dependency, and it is optional.
- **Do not poll.** Every ranking and every `/next` replays the list's entire
  comparison history. One request per answer, no background refresh.

### Test plan

`:client` unit tests against MockWebServer for the login handshake, the CSRF
header, the 403-HTML path, 400 field errors, and the comparison POST body. Then
a local Django instance (Path B) for integration, before anything touches the
network.

Live smoke tests are authorized **only** against the `fe3h husbandos` and
`fe3h waifus` lists, which the owner confirmed on 2026-09-02 are disposable.
Never against the Extensive Character List — it holds the real comparison
history, and `DELETE` endpoints work.

### Phasing

| Phase | Deliverable | Buildable in a cloud session |
| --- | --- | --- |
| P0 | `:client` + its tests | **Done** (2026-09-02) |
| P1 | `:app`: login and the sort loop | No — needs the SDK |
| P2 | Rankings screen, in-run undo | No |
| P3 | Offline queue (Room + backdated `timestamp`), images, graph | No |

P0 is the whole of the risk and none of the toolchain, so start there.

**P0 landed on 2026-09-02**: `android/` is a Gradle build with a `:client`
module (Kotlin/JVM, OkHttp, kotlinx.serialization) covering the handshake, the
cookie jar, the CSRF interceptor and the five endpoints the prototype needs.
32 MockWebServer tests pass on JDK 21 with no Android SDK, and two read-only
`LiveSmokeTest` probes — skipped unless `CHARSORTER_LIVE=1` — confirm the real
site's 401 envelope and login page against the client's own parsing. See
[`android/README.md`](android/README.md). Nothing has been written to the live
database: no credentials exist in a cloud session.

Two things P0 could not do, and P1 inherits: the local Django instance under
"Open risks" was still not stood up, so nothing has exercised a real `201` from
`POST /comparisons`; and the CSRF-retry path is proven against MockWebServer
only, never against a genuinely stale token.

## Open risks

- **Deployment is no longer a gate.** Upstream responsiveness was the headline
  risk; it resolved with three merged PRs, and the deploy followed the same
  day. Re-verify after any future server-side change rather than assuming a
  merge reached the host — the check is cheap and needs no credentials.
- **No local instance yet.** The API is live and its `DELETE`s work, so the
  first client written against it should talk to a local server, not to the
  maintainer's production data. Nothing in this repo runs on a modern
  interpreter without the uncommittable Path B edits, so standing one up is
  the real prerequisite for client work.
- **Iteration still routes through him.** The API buys independence for
  *clients*; changing the API itself is another PR and another deploy. Batch
  server-side changes accordingly.
- **Secrets.** `local_settings.py` is tracked despite `.gitignore` and carries a
  `SECRET_KEY` and Postgres password. Rotating them is a prerequisite for any
  real deployment of this fork.
