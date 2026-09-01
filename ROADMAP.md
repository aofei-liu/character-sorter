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

### PR 1 — Authorization fixes

Close the POST-body holes documented under "Known issues" in `CLAUDE.md`:

- Scope `undo`'s `SortRecord` lookup to `list_id`.
- Filter `ModifyCharFormset` / `ModifyCharlistFormset` querysets on POST.
- Validate the hidden `characterlist` / `owner` fields against `request.user`
  instead of trusting them.

Pure bug fix, no behavior change for honest users, roughly 30 lines. Also the
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

Only needed for a native app, or to iterate without waiting on upstream reviews.

**Do not add Django REST Framework.** Modern DRF requires Django 4.2+, so
upstream would need a pinned 2018 release. This app has six views returning
plain Python data already; hand-rolled `JsonResponse` views add no dependency,
behave identically on Django 2.0 and 5.x, and are far likelier to be merged.

Auth: a simple opaque token model plus an `Authorization: Token <key>` header.
Session cookies work for a PWA but are awkward from native code. JWT/OAuth2 is
overkill for a single-user hobby app.

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/auth/token` | username + password → token |
| `GET` `POST` | `/api/lists` | list / create `CharacterList`s |
| `GET` `PATCH` `DELETE` | `/api/lists/<id>` | ranked chars, annotations, progress |
| `GET` `POST` | `/api/lists/<id>/characters` | list / add `Character`s |
| `PATCH` `DELETE` | `/api/characters/<id>` | edit / remove one |
| `GET` | `/api/lists/<id>/next` | next comparison pair (+ image URLs) |
| `POST` | `/api/lists/<id>/comparisons` | `{char1, char2, value, timestamp?}` |
| `DELETE` | `/api/lists/<id>/comparisons/<rec_id>` | undo — **must** verify `rec.charlist_id == list_id` |
| `GET` | `/api/lists/<id>/graph` | Glicko rating / RD arrays |

The `controller/` layer already returns plain Python data and needs **no
changes** to back any of this. All the work is in `sorterinput/views.py`.

Three properties of the existing code will shape the client (all documented in
`CLAUDE.md`):

- **`SortRecord.timestamp` is `auto_now_add=True`**, so accepting a
  client-supplied timestamp — needed for offline queueing — means overwriting it
  after `save()`. Safe, because `compute_ratings` sorts by timestamp before
  replaying.
- **`get_next_comparison` is non-deterministic for Glicko.** It samples from a
  softmax, so two `GET /next` calls return different pairs. Fine, because
  `POST /comparisons` names `char1` and `char2` explicitly — but a client must
  not assume it can re-fetch "the same" pending question.
- **Every ranking request replays the entire history.** Acceptable for a page
  load; it becomes the hot path when an app polls for comparisons. If it gets
  slow, cache on `SortRecord` count + max timestamp — but do **not** reuse the
  per-request `dirty` flag, which is explicitly not valid across requests.

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
