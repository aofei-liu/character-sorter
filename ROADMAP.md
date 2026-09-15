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

Anything running on the upstream host must run on **Django 2.0.6 / Python
3.5.2** — read off the server's `~/env` venv on 2026-09-13 (`django --version`
→ 2.0.6, `python` → 3.5.2), not the 3.7 assumed earlier. 3.5 is the stricter
floor (no f-strings, no variable annotations, no insertion-ordered dicts), and
the Path A check below ran on a 3.7.12 sandbox, so it did not exercise the true
prod interpreter. The modernization path in `CLAUDE.md` ("Path B") still applies
to *local development of this fork*, but no upstream PR may depend on a modern
Django.

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
header, the 403-HTML path, 400 field errors, and the comparison POST body — 35
of them, all offline. Then `LocalServerIntegrationTest` against a local Django
instance (Path B), gated on `CHARSORTER_LOCAL` the way the smoke tests are
gated on `CHARSORTER_LIVE`, for the two paths a mock can only imitate: a real
`201` from `POST /comparisons` decoded and undone, and the 403 → refresh →
retry driven by a genuinely stale `csrftoken`. Both were run green on
2026-09-06; `android/README.md` has the command and the env vars.

Live smoke tests are authorized **only** against the `fe3h husbandos` and
`fe3h waifus` lists, which the owner confirmed on 2026-09-02 are disposable.
Never against the Extensive Character List — it holds the real comparison
history, and `DELETE` endpoints work. The same rule binds
`LocalServerIntegrationTest`: it writes and deletes records, so it must point
at `127.0.0.1`, never at the deployed host.

### Phasing

| Phase | Deliverable | Buildable in a cloud session |
| --- | --- | --- |
| P0 | `:client` + its tests | **Done** (2026-09-02) |
| P1 | `:app`: login and the sort loop | **Done** (2026-09-10) |
| P2 | Rankings screen, in-run undo | **Done** (2026-09-10) |
| P3 | Superseded — see "Feature queue" below | — |

P0 is the whole of the risk and none of the toolchain, so start there.

**P0 landed on 2026-09-02**: `android/` is a Gradle build with a `:client`
module (Kotlin/JVM, OkHttp, kotlinx.serialization) covering the handshake, the
cookie jar, the CSRF interceptor and the five endpoints the prototype needs.
35 MockWebServer tests pass on JDK 21 with no Android SDK, and two read-only
`LiveSmokeTest` probes — skipped unless `CHARSORTER_LIVE=1` — confirm the real
site's 401 envelope and login page against the client's own parsing. See
[`android/README.md`](android/README.md). `.github/workflows/android-client.yml`
runs the build and the offline tests on every push that touches `android/`.
Nothing has been written to the live database.

**The two gaps P0 left are now closed (2026-09-06).** A local Django instance
was stood up on Path B (`Django==4.2.16` on Python 3.11, SQLite), and
`LocalServerIntegrationTest` — seven tests, gated on `CHARSORTER_LOCAL` — runs
the real `CharSorterClient` against it: the login handshake, a real `201` from
`POST /comparisons` decoded into a `Comparison` and undone, a backdated
`timestamp` round-trip, and the 403 → token-refresh → retry path against a
`csrftoken` that Django genuinely rejects rather than an enqueued 403. All
seven pass. The Path B edits stay uncommitted, per `CLAUDE.md`.

### P1 decisions (2026-09-07)

Settled before any `:app` code is written, so the next session starts from a
spec rather than re-deriving it. No code exists yet.

**Scope: prototype only.** Log in, pick a list, answer comparisons, view the
ranking. Explicitly out — images, the graph, list/character editing, and the
offline queue. Those are P2/P3; folding them in now would widen the first
`:app` diff past what is reviewable.

**`:client` needs no changes for P1.** Its surface — `login`, `lists`,
`nextComparison`, `submitComparison`, `deleteComparison`, `ranking`,
`cookieJar.save()/restore()`, `isLoggedIn` — already covers the prototype.
Undo stays in-run only; the `GET /comparisons` endpoint that would let it
survive a restart is still deferred and is not a P1 dependency.

**Toolchain lives on the WSL box, under `~`.** The SDK is installed with
`cmdline-tools` + `sdkmanager` into a user directory — no `sudo`, no system
package. This is possible here because the box's network reaches
`dl.google.com` and Google's Maven; the "No — needs the SDK" cells in the
Phasing table were written for cloud sessions, where those hosts are off the
allowlist, and do not apply to this machine. `android/settings.gradle.kts`
gains `google()` in both repository blocks and `include(":app")`.

**Delivery: sideload first, emulator for iteration.** The target is a debug
APK (`./gradlew :app:assembleDebug`) copied to a physical phone — the "real
phone" goal, and it needs no emulator or USB passthrough. An emulator is also
stood up on the WSL box for fast build-and-look cycles: `/dev/kvm` is present,
so it is KVM-accelerated, and it needs neither a GPU nor WSLg —
`-gpu swiftshader_indirect` (software rendering) and `-no-window` (headless,
screenshot via `adb exec-out screencap`) both work, so WSLg GPU flakiness
cannot block it.

**`minSdk = 26`, `compileSdk = 34`, `applicationId
io.github.aofeiliu.charsorter`.** `minSdk 26` (Android 8.0) is the floor at
which `java.time` ships in the platform runtime, so `:client`'s
`OffsetDateTime` in `submitComparison` needs no core-library desugaring — one
fewer moving part, at the cost of pre-2017 devices, which the owner does not
use. `compileSdk` is the API level the module compiles against and is
independent of that floor. The app id matches the `:client` package namespace.

### P1 progress (2026-09-10): toolchain installed

Step 1 of P1 — stand up the build toolchain on the WSL box — is done. No
`:app` code exists yet; the next step is wiring `:app` into the Gradle build.

Everything is under `~/opt`, installed without `sudo`:

| Component | Version | Path |
| --- | --- | --- |
| JDK (Temurin) | 21.0.12.1 | `~/opt/jdk-21` |
| Android `cmdline-tools` | 12.0 | `~/opt/android-sdk` |
| `platform-tools` | 37.0.1 | |
| `platforms;android-34` | rev 3 | |
| `build-tools;34.0.0` | 34.0.0 | |
| `emulator` | 37.1.11 | |
| `system-images;android-34;google_apis;x86_64` | — | AVD `charsorter34` (Pixel 6) |

`~/opt/android-env.sh` exports `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`
and `PATH`; `~/.bashrc` sources it. Non-interactive shells (including anything a
Claude Code Bash call runs) do **not** get it — `~/.bashrc` returns early for
those — so a script must `. ~/opt/android-env.sh` explicitly. Footprint is
~5.9 GB (5.5 GB SDK, 346 MB JDK).

Validated: the Gradle wrapper (8.14.3) downloaded and ran `:client:test` green
on the new JDK.

### P1 progress (2026-09-10): `:app` wired in, all four screens compile and run

`settings.gradle.kts` now includes `:app`; `android/app` is a Compose module
(AGP 8.7.3, Kotlin's `org.jetbrains.kotlin.plugin.compose` — no separate
Compose Compiler version to track, since Kotlin 2.0 folded it into the
K2-aligned Gradle plugin) depending on `:client`, with `minSdk 26`,
`compileSdk 34` and `applicationId io.github.aofeiliu.charsorter` as decided
above.

**Architecture:** a sealed `Screen` (`Login`, `PickList`, `Sorting`, `Ranking`)
held in one `AppViewModel`, switched on in `CharSorterApp`'s top-level
composable — no Navigation-Compose dependency; four screens don't earn it.
`AppViewModel` owns the single `CharSorterClient` instance and runs every
client call (blocking by design) on `Dispatchers.IO` via `viewModelScope`.
`NotAuthenticatedException` drops the state back to `Login` and clears the
stored session — retrying a stale 401 without a fresh login would just repeat
it. Every other `ApiException`/`IOException` surfaces in a dismissable
Snackbar shared across all four screens. `SessionStore` persists
`cookieJar.save()` in `SharedPreferences` across process restarts; only the
cookies are stored, never the password, per `SessionCookieJar`'s own
docstring.

**Verified on the WSL box**, not a cloud session — this machine's
`dl.google.com`/Google-Maven reachability is what makes any of this possible,
per "Toolchain lives on the WSL box" above:

- `./gradlew :app:assembleDebug` and `:client:test` both pass.
- Booted `charsorter34` headless (`-gpu swiftshader_indirect -no-window
  -memory 1536`), installed the debug APK, and screenshotted the running app
  via `adb exec-out screencap` — the login screen renders correctly (title,
  two fields, a "Log in" button that is correctly disabled while either field
  is empty).
- The emulator was torn down immediately after the screenshot rather than
  left running. **This box's free RAM is tighter than the P1 decisions
  assumed:** `free -h` reports 7.7 GiB total, not the 16 GiB in
  `~/.claude/CLAUDE.md`'s hardware table — evidently a `.wslconfig` cap, not
  yet reconciled with that doc — and booting the emulator alongside the
  already-running Gradle/Kotlin daemons pushed available memory to ~1.1 GiB
  with swap engaged. It booted and ran without failing, but there is no
  headroom for a second concurrent job (another emulator instance, a second
  Gradle build, or Claude Science) while it's up.
- **Not attempted:** logging in against the real
  `charsorter.lndyn.com` account. That needs real credentials, which no
  session here holds, and doing it from a screen that had never been
  human-reviewed felt like the wrong first test of a live-writing credential
  path. The auth handshake itself is covered by `:client`'s MockWebServer
  tests, by `LocalServerIntegrationTest` against a real Django, and by the
  `LiveSmokeTest` read-only probes; this is only "does the UI screen call it
  correctly," and that is unverified.

### P1 confirmed live, and P2 done (2026-09-10)

The owner sideloaded the debug APK onto a real phone and **logged into the
live site successfully**, reaching the list picker, the sort loop and the
ranking screen against real data.

That closes the `:app` gap specifically — that the UI screens drive the client
correctly, end to end, against the deployment rather than against a mock. It
is **not** the first exercise of the protocol itself: `LocalServerIntegrationTest`
had already covered the login handshake, a real `201` from `POST /comparisons`
and the CSRF-refresh retry against a local Django on 2026-09-06 (see the P0
note above). An earlier revision of this section claimed otherwise, having
been written on a branch cut before that work merged.

Two changes came out of that session, which also finish P2:

- **The comparison cards stack top/bottom, not side by side.** Reported from
  the phone: two half-width columns are too narrow at a portrait aspect
  ratio, so names wrap badly while most of the vertical space goes unused.
  Prefer vertical stacking for any two-option choice UI in this fork — the
  web sort page has the same pattern and the same target device.
- **In-run undo.** `AppViewModel` keeps an `undoStack` of the `Comparison`
  records this process posted; an Undo button on the progress line pops the
  last one via `DELETE /comparisons/<rec_id>` and re-fetches `/next`. It sits
  away from the three answer controls so it cannot be mis-tapped mid-sort,
  and it is disabled while a request is in flight. The entry is popped only
  after the server accepts the delete, so a failed undo can be retried.

  The stack is deliberately not persisted. A record id is only ever seen in
  the `201` from our own `POST` — there is no `GET /comparisons` — so undo
  reaches back exactly as far as this process does. Worth knowing when using
  it: for a Glicko list, `/next` re-samples afterwards, so undo takes the
  record back but does **not** re-ask the question just answered.

**Not yet verified by anyone but the owner's own phone.** No session on this
box holds credentials for the live site, so every screen behind the login is
unverifiable here — a cloud or WSL session can confirm the app compiles,
installs and renders the login screen, and nothing further. Treat "it builds"
as a much weaker claim than usual for this module.

**Next step:** see "Feature queue" below, which replaces the old one-line P3.

Two corrections to the P1 decisions above:

- **KVM was not usable out of the box.** `/dev/kvm` is present but is
  `root:kvm` mode `0660`, and the login user was not in the `kvm` group, so
  `emulator -accel-check` failed. Fixed with `sudo gpasswd -a $USER kvm` (the
  one step in P1 that needed `sudo`). After that, `-accel-check` reports "KVM
  (version 12) is installed and usable".
- **The group change does not reach an already-running shell.** A session
  whose shell started before the `gpasswd` must wrap emulator commands in
  `sg kvm -c '...'`; any new WSL terminal picks the group up at login.

## Feature queue

Revised 2026-09-10 (second pass), with the owner's calls recorded so they are
not relitigated. This replaces the single P3 row in the phasing table, which
bundled unrelated features into one line and hid the fact that the one most
wanted is blocked on the server, not on effort.

| # | Item | Size | `:client` work | Blocked on |
| --- | --- | --- | --- | --- |
| 1 | List and character editing | Large | Substantial | **Done** (2026-09-11) |
| 2 | Per-character ranking history plot | Large | Yes | **Done** (2026-09-12) |
| 3 | Whole-list Glicko chart | ~200 | Yes | **Done** (2026-09-11) |
| 4 | Small hardening | ~50 | None | **Done** (2026-09-12) |
| 5 | Dedup the `/next` replay | ~30 | None | **Done** (2026-09-12) |
| 6 | Batch add characters by paste | Large | Yes | **Done** (2026-09-12) |
| 7 | Reorder and delete lists from the picker | ~280 | Yes | **Done** (2026-09-12) |

**Deferred by decision, not forgotten** (2026-09-10):

- **Images on the sort cards.** "We don't really need images tbh, we can add
  it at a later date." If revived, check the precondition first: images only
  render when a list has `show_images` on, and upstream's
  `MaybeAppendShowImages` removes that field entirely when `IMAGE_SEARCH_KEY`
  is `""`. One authenticated `GET /api/lists` settles whether the feature can
  work at all before any code is written.
- **Offline queue.** Not needed now. The analysis is kept below so the design
  question is not re-derived from scratch later.

### 1 — List and character editing

**Next up.** The reason is scope of use: without it the app can read and sort
but not maintain a list, so the desktop site is still required for ordinary
upkeep. Editing is what makes the phone a replacement rather than a companion.

The API offers full CRUD — `POST /api/lists`, `PATCH`/`DELETE` on a list,
`POST /api/lists/<id>/characters`, `PATCH`/`DELETE` on a character — and
`:client` implements **none** of it. Its entire surface is `login`, `lists`,
`ranking`, `nextComparison`, `submitComparison`, `deleteComparison`. So this
is client methods, models and MockWebServer tests first, then UI.

That split is a feature here, not a chore: `:client` is the only module any
session on this box can actually verify, so putting the request shaping,
error mapping and field-level validation there means most of this entry is
testable before it ever reaches a screen.

**Done 2026-09-11**, as two commits on one branch rather than the two PRs
first planned — the `:client` half was held back so the owner could test the
whole feature on a phone before any of it was reviewed. `:client` gained seven
methods and 11 MockWebServer tests; `:app` gained an edit screen reached from
a third per-list action, with add, edit and delete for characters and rename
and delete for the list. Verified on device: a list created and deleted end to
end, and both orderings of the character rows.

The edit screen can also order by score, which is not free and so is a chip
rather than the default. `GET /characters` carries no rating at all — the
ranking endpoint is the only place a score exists, and it replays the list's
whole comparison history server-side. Ranked order is therefore refetched
after every write while the chip is on, since a delete re-ranks everyone.

Two capabilities exist in `:client` but are deliberately not exposed in the
UI: changing a list's `controller_type` after creation, which is meaningless
once comparisons exist, and `show_images`, which belongs with the deferred
images entry.

These are also the destructive endpoints, run against the live production
database. The standing rule applies: smoke-test only against the two
disposable lists confirmed on 2026-09-02, never against the list holding the
real comparison history. `DELETE` on a character is not recoverable through
any surface this fork has.

### 2 — Per-character ranking history plot

The feature the owner actually wants: tap a character in the ranked list and
see how its rating has moved over time, zoomable. It is also the better
phone-native answer to "how is this list doing" than entry 3, because it shows
one character at a time instead of asking a phone screen to render hundreds of
bars at once.

**It is blocked on the server, and the reason is worth stating precisely.**
Verified against the source, not the notes:

- `SortRecord` holds every comparison with a timestamp, and `compute_ratings`
  replays them in timestamp order in a single pass. So rating history *is*
  derivable from data the server already stores.
- Nothing exposes it. `GET /api/lists/<id>/graph` calls `get_graph_info`,
  which replays and then emits only the **final** state — one rating and one
  `2*RD` per character. There is no historical anything in the API today.
- `lists/<id>/comparisons` **already exists as a route** but is decorated
  `@api_view("POST")`, so a `GET` returns 405, not 404. The earlier note in
  this file that there is "no `GET` on `/comparisons`" was right about the
  behaviour and misleading about the cost of fixing it.

Two shapes for the upstream ask:

**(a) Expose the raw records.** `api_view` takes varargs, so this is widening
one decorator to `@api_view("GET", "POST")` and adding a branch that
serializes the list's records — about as small as an upstream PR gets, on a
route that already exists. The client then replays the ratings itself.

**(b) Compute the history server-side.** A new endpoint that replays once and
emits, per character, its rating after each match it played. Payload stays
sane because a rating only changes when that character plays: every record
touches exactly two characters, so the total series length is `2 x records`,
not `characters x records`. Server cost is one pass — the same order as any
ranking request, which already replays everything.

**Recommendation: (a).** It is the smaller ask on a maintainer's schedule, and
it unblocks three separate things at once — this plot, durable undo (a record
id currently only ever arrives in the `201` from our own `POST`), and any
future offline pair selection. After it lands, the fork iterates on all three
without asking again.

The cost of (a) is porting the Glicko replay to Kotlin, which earlier notes
treated as prohibitive. It is smaller than it sounds: history needs only
`process_record` and the RD decay, **not** the two-step softmax match
selection, which is the genuinely gnarly part and is irrelevant to replaying
what already happened. The port must match the server's constants
(`CONFIDENCE_BOOST = 2`, `RD_RESET_TIME` of 90 days, the defaults) and belongs
in `:client`, where it can be tested. Drift would show up as the app
disagreeing with the website about the same list, so test it against a real
list's `/graph` output, whose final state the replay must reproduce exactly.

Opening the upstream PR is straightforward from a local session: `gh pr
create --repo jerrywu64/character-sorter --head aofei-liu:<branch>` works,
and the compare link is the fallback where `gh` is not available. See
"Opening a PR against upstream" in `CLAUDE.md`. The friction that remains is
what happens after: the change is only live once upstream merges *and*
redeploys.

**Done 2026-09-12**, as shape (b) rather than the recommended (a): a
server-side `GET /api/lists/<id>/characters/<id>/history` that replays once and
emits the character's rating and RD after each match it played. (a) was dropped
because porting the Glicko replay to Kotlin bought nothing the fork needed
today, and a wrong port would have surfaced as the app disagreeing with the
website. Opened upstream with `gh` as `#13` on 2026-09-11, merged 2026-09-12,
and live — the trend screen works against `charsorter.lndyn.com`.

`:app` gained a trend screen reached by tapping a ranked row: the rating line
with its `2 * rd` band, dated at both ends, above a reversed match list. The
chart scales to the line and not the band, since an early RD near 350 squashes
the trend into a strip, and a history longer than 80 points is thinned with
Largest-Triangle-Three-Buckets so the peaks survive.

The plot itself is app-only by design, and stays that way — the website gains
no trend view, and further visualization work is expected to land in the app
rather than upstream. Only the endpoint had to be shared, because the ratings
it replays live in the production database.

**Resolved 2026-09-13:** the endpoint's source was missing from fork `main`
for a while — it existed only on `claude/rating-history-upstream` — so this
repo's own Django app 404d on a route production served, which stopped
`LocalServerIntegrationTest` from reaching the trend screen. Fork `main` has
since been synced with upstream and carries it.

### 3 — Whole-list Glicko chart

Wanted, but it needs a readability answer before code. The web version is a
Plotly bar-with-error-bars across every character, and a phone cannot render
hundreds of bars legibly. Candidates, in rough order of promise:

1. **Horizontal bars in a scrolling list**, one row per character, rating with
   an RD whisker. Phone-native, reuses the ranking screen's layout, and scales
   to any list length.
2. Vertical bars on a horizontally scrollable canvas — closest to the desktop
   chart, worst on a phone.
3. Top-N plus search, which answers a different question than the desktop
   chart does.

`:client` needs a `graph()` method, models and tests either way; the endpoint
exists and the client never implemented it. `/graph` returns real JSON arrays
(the endpoint parses `get_graph_info`'s `json.dumps`'d strings once), so the
client does not inherit the `graph.html` XSS. Only Glicko lists have a graph —
`get_graph_info` returns `None` for insertion sort, and the endpoint 404s —
so the entry point must be conditional on `controller_type`.

If entry 2 ships first, revisit whether this is still wanted; the drill-down
may cover the need.

**Done 2026-09-11**, as candidate 1. One row per character: the rating as a
marker, `2 * rd` as a whisker, on a scale shared by every row, reached from a
"Chart" button the ranking screen shows only for a Glicko list. Bars were
dropped along the way — a bar's length reads as a magnitude measured from
zero, and a Glicko rating has no zero — so the row is a marker-and-whisker
rather than the web chart's bar. It answers the ranking's own puzzle for
free: a character can sit below one it outrates, and the row shows why, in
the whisker reaching further left.

Entry 2 shipped first and did not cover the need: the drill-down answers
"how did this one get here", the chart answers "how do they compare now".

### 4 — Small hardening

Unrelated papercuts, independent and pick-up-anytime:

- **The sort loop flickers.** Every answer swaps the whole card area for a
  full-screen spinner until the next pair arrives, so fast sorting stutters on
  every tap. This is the one here that is felt daily; keeping the cards up and
  showing progress more subtly would make the core loop feel markedly better.
- **Login errors are vague.** A field-level rejection from the server carries
  `InvalidRequestException.fields`, but the form shows only the generic
  Snackbar text, so "which field" is lost.
- **Long titles crowd the header.** A long list title squeezes the "Lists"
  button on the sort screen.
- **The ranking screen has no retry.** A failed load leaves `ranking` null,
  which that screen renders as a spinner forever; the only way out is backing
  out to the list picker and re-entering. The sort screen grew a Retry branch
  when the duplicate-comparison bug was fixed — this is the same pattern, and
  the same three lines.

**Done 2026-09-12**, all four in one branch, as four commits:

- The flicker fix reorders `SortScreen`'s `when` to check `pending` ahead of
  `busy`, so a pair already on screen stays up (dimmed, non-clickable) for the
  round trip that answers it. The spinner now only covers a load with nothing
  to show yet.
- **"Login errors are vague" turned out to be misnamed against the source.**
  `client.login()` only ever throws `LoginFailedException`, never
  `InvalidRequestException` — the login form was never the one losing field
  detail. The real gap is `AppViewModel.runApiCall`'s generic catch, shared by
  every screen with a form (add character, create list, rename, …), which
  rendered any `ApiException` as `err.message` and dropped `.fields` on the
  floor. Fixed there instead, which covers login's own future error shapes
  for free if the server ever changes how it reports one.
- The long-title fix turned out to apply to four screens, not one:
  `SortScreen`, `RankingScreen`, `EditListScreen` and `ListChartScreen` all
  share the same weighted-Text-next-to-a-button header, so all four titles now
  cap at one line with an ellipsis rather than only the sort screen's.
- The ranking screen's retry is the same three-line pattern as the sort
  screen's, plus a `loadRanking()` on `AppViewModel` for it to call.

A note on where these keep coming from: the duplicate-comparison bug fixed in
this PR lived in `AppViewModel`'s state machine, which is exactly the "risky
logic" the module split was meant to keep in `:client`, where it would have
been testable. `:app` has no tests and cannot be verified in any session here.
Logic that can be stated as a rule about requests and responses belongs on the
`:client` side of the line.

### 5 — Dedup the `/next` replay

Not requested yet, but earned by real numbers: a seeded benchmark (30
characters, 100/1k/5k comparisons, scratch SQLite via `manage.py shell`, not
the production host) shows the replay cost scaling **linearly** with
comparison count, which is the good news, and `/next` paying it **twice**,
which is the bad one:

| comparisons | `compute_ratings` (cold) | `get_last_matches` | `/next` | `/api/lists/<id>` | `/graph` |
| --- | --- | --- | --- | --- | --- |
| 100 | 9ms | 7ms | 26ms | 18ms | 15ms |
| 1,000 | 59ms | 48ms | 118ms | 67ms | 63ms |
| 5,000 | 341ms | 292ms | 671ms | 337ms | 336ms |

`get_next_comparison` calls `compute_ratings` (one replay), then separately
calls `SortRecord.get_last_matches` — a second, independent query and replay
of the same table — to find each pair's last meeting. `/next` is the sort
loop's own endpoint, the action repeated most in the whole app, so it is the
one that will be felt first as a list grows. The owner's largest list is
already estimated at 500-1,500 comparisons from one character's 113 matches
alone, which lands in the tens-to-~150ms band today and grows as the list
does.

**The fix: fold `get_last_matches` into the pass `compute_ratings` already
makes.** `compute_ratings` already loads
`charlist.sortrecord_set.all().order_by("timestamp")` into `records` and
walks them once; `get_last_matches` re-queries and re-walks the same table to
find each pair's most recent match. Deriving that from the records
`compute_ratings` already has removes one of `/next`'s two replays with no
behavior change and no new failure mode — a same-file refactor, not a cache.
At 5,000 comparisons this should cut `/next` from ~670ms toward the ~340ms a
single replay costs today.

**Caching the replay result itself across requests was considered and
rejected.** `CLAUDE.md` documents "nothing is denormalized or cached...
rankings are always recomputed... on every request" as the app's current
invariant, and trading that guarantee for speed was judged too dangerous — a
caching bug means a stale ranking is shown as current, which is a worse
failure than a slow page. This entry is the dedup only; the earlier gotcha
note (PR 3a section, above) proposing "cache on count + max timestamp" stays
unimplemented for the same reason, and is also wrong as written — comparisons
can be backdated (`client_timestamp`), so "max timestamp" does not change on
every write and would invalidate incorrectly if anyone ever built it.

This needs an upstream PR like the XSS fix, not a fork-only change:
`charsorter.lndyn.com` is deployed from what the maintainer merges into
upstream `main` (the same model already established for `#11`), and the
mobile client depends on *that* server being fast, not on anything committed
to this fork.

**Done 2026-09-12**, and verified on Path A this time: a conda-forge Python
3.7.12 sandbox with the exact pinned `Django==2.0.6`/`numpy==1.14.5`/
`scipy==1.1.0` installs cleanly (`uv` has no standalone 3.7 build; micromamba
does). `manage.py check` and a plain `migrate --run-syncdb` are clean, but
`manage.py test` itself fails independent of this change — `sorterinput`
migration 0003's `AlterField` rebuilds the `characterlist` table, and Django
2.0.6's SQLite schema editor leaves `character`'s FK clause referencing the
now-gone `..._characterlist__old`. Reproduced on the pre-fix code too, so it
is a real Django 2.0.6 + SQLite bug — production runs Postgres and never
takes this table-rebuild path, so it has no bearing there. Worked around it
for verification only (disabling SQLite FK enforcement on the connection) and
confirmed directly against real Django/numpy/scipy: `self.last_matches`,
built inline during `compute_ratings`, matches `SortRecord.get_last_matches`
exactly, and `get_next_comparison`/`get_sorted_chars`/`get_annotations`/
`get_graph_info` all still run clean. Also reran the full suite under Path B:
20/20.

### 6 — Batch add characters by paste

A "Paste many" screen: paste text, confirm the parse, post. Parser in
`:client` with 23 tests.

Two line formats, mixable: inline `Name (Fandom)`, and a `[Fandom]` header
supplying the fandom for bare names beneath it. The header form exists because
a fandom is **mandatory server-side** (`CharacterForm` over
`["name", "fandom"]`, and `Character.fandom` has no `blank=True`), so a bare
name would be rejected.

Decisions, not to be relitigated:

- Inline fandom is a one-line **override**, not sticky.
- Duplicates are **skipped**, matched case-insensitively on name *and* fandom.
  A repeat within one paste reports separately from one already in the list.
- Tab beats trailing parens; `[]` clears the fandom; empty parens fall back to
  the header.
- Preview groups by **resolved** fandom — the only view that shows a header
  applied to the wrong names. Nothing is written until it is confirmed; the
  text lives in `UiState`, so preview→editor is lossless.

Rejected: **TSV import** (the friction is getting the file onto the phone, and
whoever prepares one is at a desktop where the website already works) and
**Keep/Docs import** (Keep has no official API; Docs means a Cloud project and
OAuth beside our one session cookie). If the Keep case returns, the cheap route
is an `ACTION_SEND` share target into the same parser — one intent filter plus
a list-picker step, since a share carries text but no list.

Server constraints: no bulk endpoint, so a batch is N sequential POSTs. It does
not reuse `addCharacter`, which refetches after every write; it posts N and
refetches once. It stops at the first failure and keeps the paste text, so a
re-run is safe — the refetch marks whatever landed as already in the list.

**Ported upstream as a paste box on the edit page** — `#16`, opened and
merged 2026-09-13, 135 lines plus 89 of tests. Same parse rules, reimplemented
in `sorterinput/paste.py`; the Kotlin is not reusable, only the spec above is.
It went in as a merge commit rather than a squash, so unlike `#10`-`#12` the
branch really is an ancestor of upstream `main`. **Deployed**, confirmed by
the owner against the live site the same day. That check cannot be automated
the way `#11`/`#12` were: the paste box sits behind login, so there is no
anonymous signal of the kind `/api/`'s 401 gives. In fork `main` since the
2026-09-13 sync.

- **No preview step.** The confirm screen guards against a partial batch
  across N requests; one `bulk_create` in one POST cannot half-fail.
- Parsing sits outside the view so it is testable without a request. The view
  adds one `values_list` dedup query and one `bulk_create`, then redirects.
- Counts and the first five skipped lines are reported through the messages
  framework, which was already configured and unused.
- The paste branch must precede the formset branch: reaching
  `modformset.is_valid()` with no management form raises rather than
  returning `False`.
- `bulk_create` skips model validation, so the parser enforces the 200-char
  field limit itself.
- Verified on Path B only (37/37 on the branch). No Python 3.7 here, and the
  PR body says so rather than implying the pinned stack was exercised.
### 7 — Reorder and delete lists from the picker

An "Arrange" mode on the picker: up/down per row, plus delete. Delete was
nearly free — `deleteList` and the destructive-confirm dialog both already
existed. The confirm stays, because deleting a list cascades its characters
*and* its whole `SortRecord` history, unrecoverably.

Reorder had nowhere to store an order: `CharacterList` has no position field
and `api.lists()` hard-codes `.order_by("id")`.

Decisions, not to be relitigated:

- **The order is phone-local.** A server-side field would mean a migration
  against the live production Postgres, an upstream PR, a deploy and a backfill
  — too much for a cosmetic feature. Accepted cost: per-device, invisible to
  the website. If it is ever wanted server-side, the local order becomes the
  cache in front of the field.
- **Up/down arrows, not drag.** Compose BOM 2024.10.01 has no reorderable
  `LazyColumn`, so dragging meant a third-party dep or ~200 lines of gesture
  code in `:app`, the one module no session here can verify.

Reconciliation lives in `:client` with 8 tests: prune ids the server no longer
returns, append unseen lists in the server's own order (so a list made on the
website still appears), otherwise preserve the saved order. It runs inside
`loadListsBlocking` — the single point lists enter state — so a list deleted on
the website prunes itself and `deleteList` needed no change. Prefs hold a
delimited string, not `SessionStore`'s `StringSet`, since a set has no order.
Arrange mode is screen-local; the order it produces persists.

### 8 — Decay tuning, exposed as per-list settings

Eight years of list 3 (77 characters, 1,912 comparisons) say the decay constant
is the problem, not the rating model. Repeat matchups contradict at 7-15%
across every time lag when the pair is far apart, and rise 22% -> 52% over a
year when it is close. Out-of-sample fits put the forgetting timescale at
1-2 years against the current 90 days. Live consequence: every character in
the list currently sits at RD 136-269, so nothing ever reads as settled.

Decisions, not to be relitigated:

- **Tune the constant; do not add a mechanism.** A half-life keyed on pair
  distance was proposed and rejected — the evidence does not show the rating
  system is broken, so a second decay term would be redundant and
  over-complicated.
- **`RD_RESET_TIME` is two settings wearing one name.** It sets the decay rate
  via `RD_INCREASE_SCALE_SQ` *and* caps `days_since_last` in
  `get_match_weight`, where it governs how strongly a long-unseen pairing is
  preferred. Split them: `RD_RESET_TIME` for decay, `MATCH_RECENCY_CAP` (stays
  90) for selection. Otherwise retuning decay silently makes rematches far
  less likely — at 730 a pair met 100 days ago drops to 14% of a never-met
  pair's weight, against parity today.
- **Split `DEFAULT_RD` in two.** It currently serves as both "a new character's
  uncertainty" and "the ceiling time-decay climbs to", which is why stale and
  never-compared are indistinguishable after ~90 days idle — the shared cause
  of both the no-priority-for-new-characters problem and the
  everything-resets-on-return problem. Becomes `INITIAL_RD = 700` (seeding in
  `compute_ratings` and `get_rating_history`, plus the `old_time is None`
  branch) and `MAX_DECAY_RD = 350` (the cap, and `RD_INCREASE_SCALE_SQ`), with
  `RD_RESET_TIME = 365`. A character ranked to `TYPICAL_RD` then reaches half a
  new character's uncertainty after a year and stops. Verified:
  `c^2 = (350^2 - 50^2)/365 = 328.77`, and rd 50 lands on exactly 350.0 at 365
  days.
- **Decay freezes above the ceiling; it never pulls down.** If `rd_old <
  MAX_DECAY_RD`, `rd_new = min(MAX_DECAY_RD, grown)`; otherwise rd stays put.
  Provably identical to `min(grown, max(old_rd, MAX_DECAY_RD))`. A plain
  `min(grown, 350)` would instead *lower* an RD already above 350, inventing
  confidence from nothing. Note the freeze branch is reachable by compared
  characters, not just untouched ones: a wildly lopsided first match leaves rd
  at ~680.
- **Accepted consequences of `INITIAL_RD = 700`.** An untouched character's
  ranking key is `1500 - 1400 = 100` against a live list spanning 6..3517, so
  new characters sort last until first compared (they do not linger — rd 700
  dominates the selection softmax). And `g(700) = 0.41` vs `g(350) = 0.67`, so
  a match against a brand-new character carries less information and perturbs
  the established character's rating less. Placement speed is unaffected: rd
  after six comparisons is 106 from a 700 start against 101 from 350.
- **`MIN_RD = 30` is dead.** Declared in 2018, referenced nowhere in the tree.
  Drop it with the split.
- **Settings are per-list, and the migration must not move existing data.**
  New fields default to the new values; the migration sets existing rows to
  the current ones (90 / 350). Changing live lists' behaviour on merge would
  re-rank every other upstream user.

### 9 — Chaos and focus modes

`get_next_comparison` is asymmetric — char1 is chosen by rating uncertainty,
char2 as the most informative reference — and the UI hides that, which is why
the output reads as arbitrary. Expose the asymmetry rather than change the
selection.

Decisions, not to be relitigated:

- **Chaos is the default, focus is opt-in per character, per moment.** Not a
  list-level mode. Chaos costs nothing to keep: only 4% of consecutive
  comparisons already share a char1, so today's behaviour *is* chaos.
- **Focus is client-held, not server state.** `/next` takes an optional
  `focus=<char_id>`, validated through `charlist.character_set`. No migration,
  no session state, same contract for web and app, survives two devices.
- **Stop when the opponents run out, not at an RD target.** `get_match_weight`
  is `days_since_last * inv_dsquared`, so it collapses exactly when no
  remaining opponent is a close match (irrelevant) or the close ones were just
  used (repeated) — the two things that actually annoy. Suggest stopping when
  the best available weight falls below 10% of its value at the start of the
  run. A suggestion, never a forced exit: over-focusing is cheap because the
  user can leave any time.
- **RD targets were tried and get it backwards.** Simulated on the real list:
  focusing Verso (top of the ladder) collapses to 9% of opening weight by the
  4th comparison while his RD is still 129, and he is still at RD 118 after
  fourteen — an RD <= 100 rule would never stop him, and would walk him through
  ten matchups against characters far below him. Jaime (dense middle) reaches
  RD 99 by the 6th while his opponents are still 94% as informative. Low weight
  and stubborn RD are the same fact — lopsided matchups carry little
  information — but weight reports it immediately.
- **Stateless, like `focus` itself.** `/next` returns the best available match
  weight; the client keeps the value from the run's first question and divides.
  No server-side notion of "a focus run".
- **Neighbour-bracketing rejected on evidence.** "Lost to the one above, beat
  the one below" holds for 4 of 77 characters: 69% have never met an immediate
  neighbour, and 26% have a neighbour result contradicting their rank, since
  neighbours sit inside the coin-flip band. Elegant, but it would never fire.
- **Bisection dropped.** Information-gain reference selection already tracks a
  focused character's rating as it moves, so it is adaptive placement already.
  A second placement mechanism is not worth it.
- **Forgetting to toggle is an undo.** Undo now restores the pair it took back
  rather than sampling a fresh one, so toggling focus after the fact is one
  tap plus a re-answer.
- **Insertion-sort lists ignore `focus`** and the UI does not offer the toggle
  for them; their next pair is already deterministic.
- **Re-test positional bias after labelling.** char1 currently wins 59.5%, but
  that is selection, not position — strength alone predicts 58.0%, the fitted
  position term is not significant, and the within-pair control runs the other
  way. "Shown first" is a weak cue; "this is the one being ranked" is a much
  stronger one, and this data cannot speak to it.

### Deferred: the offline queue

Kept for whenever it comes back. Queuing the writes is the easy half and is
already supported end to end: `submitComparison` takes a backdated
`timestamp`, the server accepts past timestamps, and `compute_ratings` replays
in timestamp order. Forward-dating is refused by both, deliberately.

The unsolved half is that **sorting offline needs a source of questions**, and
`/next` is server-side and re-samples on every call. Three shapes:

1. **Prefetch N pairs** from N calls to `/next`. Simplest, but all N are
   sampled against one rating snapshot, so a batch can repeat a pair or keep
   asking what its own earlier answers made uninteresting. Quality degrades
   with N.
2. **Select pairs client-side** from the full ranking and history. Correct and
   adaptive, but needs the softmax selection ported to Kotlin on top of the
   replay — a second source of truth for the algorithm. Note this becomes
   materially cheaper if entry 2 lands, since the replay half would already
   exist.
3. **Queue answers only, never ask offline.** Queue comparisons when the
   network drops mid-session, stop asking once the held pair runs out.
   Smallest and honest; probably enough for a phone that is usually online.

3 is the default unless sorting through long stretches without signal is
genuinely wanted.

### The caveat that applies to every entry

No session on this box holds credentials for the live site, so every screen
behind the login is unverifiable by anyone but the owner on a real phone. A
session can confirm that `:app` compiles, installs and renders the login
screen, and nothing further. `:client` is the opposite — fully testable here —
which is the standing argument for putting each entry's risky logic in
`:client` and keeping `:app` thin.

## Open risks

- **Deployment is no longer a gate.** Upstream responsiveness was the headline
  risk; it resolved with three merged PRs, and the deploy followed the same
  day. Re-verify after any future server-side change rather than assuming a
  merge reached the host — the check is cheap and needs no credentials.
- **Local instance is reproducible but not turnkey.** One was stood up on
  2026-09-06 (see the P0 note above) and `LocalServerIntegrationTest` runs
  against it, so client work is no longer blocked on it. But it lives in
  uncommittable Path B edits plus a scratch `dev_sqlite_settings` module —
  `CLAUDE.md` "Running the code" carries the recipe. Anyone picking this up
  rebuilds it from there; there is no committed `make dev` shortcut, and
  adding one would mean committing the Path B changes, which must not happen.
- **Iteration still routes through him.** The API buys independence for
  *clients*; changing the API itself is another PR and another deploy. Batch
  server-side changes accordingly.
- **Secrets.** `local_settings.py` is tracked despite `.gitignore` and carries a
  `SECRET_KEY` and Postgres password. Rotating them is a prerequisite for any
  real deployment of this fork.
