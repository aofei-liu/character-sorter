# CLAUDE.md

Guidance for AI assistants working in this repository.

## What this is

A Django web app for **ranking arbitrary lists of characters through pairwise
comparison**. You create a "character list", add characters to it, then
repeatedly answer "which of these two is better?" until the app can produce a
ranked order.

This repo is a **fork of [jerrywu64/character-sorter](https://github.com/jerrywu64/character-sorter)**,
which is deployed at <https://charsorter.lndyn.com/>. Upstream's default branch
is `main`. It was dormant from 2019 until 2026-09-02, when it squash-merged
this fork's three source PRs: `#10` authorization fixes, `#11` responsive UI,
`#12` JSON API. (Verified by fetching the repo — see "Opening a PR against
upstream".) All three are **deployed** as well as merged, confirmed the same
day against the running site; `ROADMAP.md` records the check and how to repeat
it.

`45a897d` is the last commit the two shared. Upstream squashed each PR into one
commit, so the two now agree on *content* but share no history past that point;
a future `git pull` from upstream would see already-applied changes as fresh
edits to the same lines. This fork's `main` additionally carries the fork-only
docs.

The fork exists to build a mobile-friendly version of the UI, which must read
and write the **live database behind `charsorter.lndyn.com`** rather than a
local copy. That constrains what code may be written here — most importantly,
anything destined for the upstream deployment must run on Django 2.0.6 /
Python 3.7. Planning, sequencing and rationale live in
[`ROADMAP.md`](ROADMAP.md); this file covers only the code as it stands.

## Repository layout

```
requirements.txt              Pinned 2018-era deps (see "Running the code")
android/                      Fork-only Android client (see android/README.md)
  settings.gradle.kts         Declares :client only; :app needs an SDK we lack
  client/                     Pure-JVM Kotlin API client + MockWebServer tests
TODO                          Upstream author's freeform backlog
README.md                     Two sentences; the security disclaimer is serious
charactersorter/
  manage.py                   Defaults DJANGO_SETTINGS_MODULE=charactersorter.local_settings
  db.sqlite3                  Tracked but EMPTY (0 bytes) — a stub, not real data
  charactersorter/            Django project package (settings, root urls, wsgi)
    base_settings.py          Shared settings, no secrets, no DATABASES
    local_settings.py         Dev settings — TRACKED IN GIT despite .gitignore
    urls.py                   Root URLconf
    wsgi.py                   Defaults to charactersorter.prod_settings (untracked)
  core/                       Auth + the base template. No models.
  sorterinput/                Models, views, forms, templates — the user-facing app
  controller/                 Sorting algorithms + the SortRecord model
```

Three Django apps, with a clear split:

- **`sorterinput`** owns the *data* (`CharacterList`, `Character`,
  `CharacterImageRecord`) and *every user-facing view*. It has no algorithm code.
- **`controller`** owns the *algorithms* (`Controller` subclasses) and the
  `SortRecord` model. It has no views (`controller/views.py` is an empty stub)
  and no URLs.
- **`core`** owns signup and `base.html`. `core/models.py` and `core/tests.py`
  are empty stubs.

## Architecture

### Data model

```
User (django.contrib.auth)
 └─< CharacterList        owner, title, controller_type ("IS"|"GL"), show_images
      ├─< Character       name, fandom
      │    └─< CharacterImageRecord   thumbnail_link, context_link (cached Google image)
      └─< SortRecord      char1, char2, value, timestamp   (defined in controller/)
```

`SortRecord.value` is the comparison result: **positive = char1 won, negative =
char2 won, 0 = tie**. This is the app's entire source of truth — rankings are
always recomputed from the full `SortRecord` history on every request. Nothing
is denormalized or cached in the database. Each record is replayed twice
(`CONFIDENCE_BOOST = 2`), and `get_last_matches` loads the history a second
time, so the cost of any ranking scales with the list's whole comparison
history.

`SortRecord.timestamp` is `auto_now_add=True`, so it **cannot be set when the
record is created**. Code that needs a specific timestamp (backdating an
import, or accepting a comparison made offline) must overwrite it after
`save()` — `controller/tests.py` does exactly this via
`rec.timestamp = ts; rec.save()`. Backdating is safe because `compute_ratings`
does `order_by("timestamp")` before replaying.

Note the cross-app FK direction: `controller.SortRecord` points at
`sorterinput` models, and `sorterinput.views` imports `controller.models`. The
dependency is mutual, done via `import controller.models` /
`import sorterinput.models` (module imports, not `from ... import`) to avoid
circular-import failures. **Keep it that way** — switching to `from x import y`
at module level in either direction will break.

### The Controller abstraction

`controller/models.py` defines an abstract `Controller` (a plain `abc.ABC`, *not*
a Django model) with this interface:

| Method | Purpose |
| --- | --- |
| `get_sorted_chars(charlist)` | List of char IDs, best → worst |
| `get_next_comparison(charlist)` | `(char1_id, char2_id)` to ask next, or `None` when done |
| `register_comparison(charlist, c1, c2, value)` | Creates a `SortRecord` (implemented in base) |
| `get_annotations(charlist)` | `{char_id: annotation}` shown next to each character |
| `get_graph_info(charlist)` | Data for the Plotly graph, or `None` if unsupported |
| `get_progress_info(charlist)` | Human-readable progress string, or `None` |

Two implementations, registered in the module-level `CONTROLLER_TYPES` dict:

**`InsertionSortController`** — binary insertion sort. Walks characters in ID
order, binary-inserting each into the sorted prefix. When it needs a comparison
that isn't in the record history, it stops and returns that pair as the next
question. Terminates: once every character is placed, `get_next_comparison`
returns `None` and the UI shows "Done!". Progress is `"{n}/{total} sorted"`.

**`GlickoRatingController`** — [Glicko](http://www.glicko.net/glicko.html)
ratings (rating + rating deviation per character). Never "finishes"; it keeps
proposing matches forever. Key behaviors:

- Characters are ranked by `rating - 2*RD` (a pessimistic lower bound), so
  uncertain characters sort lower until they've been compared enough.
- RD decays back toward `DEFAULT_RD` over `RD_RESET_TIME` (90 days) of
  inactivity — old ratings become uncertain again.
- `CONFIDENCE_BOOST = 2` counts each match twice when computing ratings.
- Match selection is a two-step softmax: pick a character weighted by RD
  (upweighted by rating via `BOOST_RATING_*`, so highly-rated characters get
  ranked more precisely), then pick their opponent weighted by expected
  information gain × days since they last met.
- Progress is a weighted average pairwise confidence, e.g.
  `"Average confidence: 0.834"`.

The class docstring explains why Glicko2 was deliberately *not* used. Respect
that decision unless asked otherwise.

**Adding a new controller** requires three coordinated edits:
1. Subclass `Controller` in `controller/models.py`.
2. Add it to `CONTROLLER_TYPES` (keyed by class name string).
3. Add a `(shortkey, "ClassName")` pair to `CharacterList.CONTROLLER_CHOICES`
   in `sorterinput/models.py` **and generate a migration** (the choices are
   baked into the field).

`sorterinput/tests.py` has a single test that asserts steps 2 and 3 stay in
sync. It will catch you if you forget one.

### The `dirty` caching flag

`Controller.__init__` sets `self.dirty = True`. Each controller caches its
computed state (`sorted_chars`, `rating_info`, …) and recomputes only when
dirty. Controllers are instantiated **per request** in
`sorterinput.views.get_list_and_controller`, so this is a within-request cache
only — it avoids recomputing ratings 3× for a single page render. It is
explicitly *not* reliable across requests (the docstring says so). Do not
promote a controller instance to module scope or a long-lived cache without
solving invalidation properly.

### Request flow

All user-facing routes live under `/sorterinput/` (`sorterinput/urls.py`),
plus `/`, `/login/`, `/logout/`, `/signup/`, `/admin/` at the root.

- `index` — lists the current user's `CharacterList`s.
- `editcharlists` — create/edit/delete lists (formset + add form).
- `viewlist` — the ranked list, with annotations and progress.
- `editlist` — create/edit/delete characters in a list.
- `sortlist` — **the main loop**: shows one pair, POSTs the answer back to
  itself, redirects to itself (POST-redirect-GET).
- `undo` — deletes the most recent `SortRecord`.
- `graphlist` — Plotly bar-with-error-bars chart of Glicko ratings.
- `cache` — pre-fetches Google images for every character in a list.

Authorization is the `@requires_list_owner` decorator in `sorterinput/views.py`:
it 404s unless the requester owns the `CharacterList` named by the URL's
`list_id` (superusers bypass). See [Known issues](#known-issues-and-gotchas) —
this decorator does *not* cover object IDs that arrive in the POST body, and
several views have holes because of that.

### Image search

`get_char_image()` queries the Google Custom Search JSON API for
`"{name} from {fandom}"` and caches the first image result in
`CharacterImageRecord`. It requires `IMAGE_SEARCH_KEY` and `IMAGE_SEARCH_CX`
settings. When `IMAGE_SEARCH_KEY == ""`, `sorterinput/forms.py`
(`MaybeAppendShowImages`) removes the `show_images` field from the forms so the
feature can't be turned on. The uncached path is slow and will crash the page if
the credentials are wrong — hence the "cache images" link on the sort page.

## The Android client

`android/` is a separate Gradle build (JDK 21, Gradle 8.14.3, wrapper
committed) and shares nothing with the Django app but the HTTP API. It is
fork-only and must never be sent upstream. `android/README.md` covers building
and using it; `ROADMAP.md` covers why it exists and what comes next.

The one rule worth repeating here: **`:client` may not depend on an Android or
AndroidX artifact.** No Android SDK can be installed in a cloud session
(`dl.google.com` is off the network allowlist), so an Android type anywhere in
that module makes the whole thing untestable here. `:app` is deliberately
absent from `settings.gradle.kts` until there is a machine that can build it.

## Settings

Three-file split, deliberately:

- **`base_settings.py`** — tracked. Shared config, `get_logging()` helper.
  No `SECRET_KEY`, no `DEBUG`, no `DATABASES`, no `ALLOWED_HOSTS`.
- **`local_settings.py`** — dev. `from charactersorter.base_settings import *`,
  then adds the missing pieces. `manage.py` defaults to this.
- **`prod_settings.py`** — **not in this repo** (gitignored, never committed).
  `wsgi.py` defaults to it. If you need to reason about production behavior,
  you're inferring — say so rather than guessing.

Any new setting that is a secret or environment-specific goes in
`local_settings`/`prod_settings`, never `base_settings`. A new *non-secret*
setting with a safe default goes in `base_settings` (that's how
`IMAGE_SEARCH_CX`/`IMAGE_SEARCH_KEY` are declared as `""`).

## Running the code

### The pinned stack does not run on modern Python

`requirements.txt` pins `Django==2.0.6`, `numpy==1.14.5`, `scipy==1.1.0` — a
mid-2018 stack targeting Python 3.4–3.7. **Verified:** on Python 3.11,
importing Django 2.0.6 fails immediately with
`AttributeError: module 'collections' has no attribute 'Iterator'`
(Python 3.10 removed the `collections` ABC aliases). The old numpy/scipy pins
also have no wheels for modern Python.

So there are two paths, and you should know which one you're on:

**Path A — reproduce the original stack.** Needs Python ≤ 3.7. There is no
pyenv/conda in the default remote container (`python3.10`–`3.13` only), so this
generally means Docker.

**Path B — modernize, for local development only.** Verified working:
`Django 4.2.16` + current `numpy`/`scipy` on Python 3.11, with **two** changes:

1. `charactersorter/urls.py` uses two removed APIs:
   - `from django.conf.urls import url` — removed in Django 4.0. Replace with
     `from django.urls import re_path as url`.
   - `auth_views.login` / `auth_views.logout` as function views with
     `{'template_name': ...}` / `{'next_page': ...}` dicts — removed in Django
     2.1. Replace with
     `auth_views.LoginView.as_view(template_name="core/login.html")` and
     `auth_views.LogoutView.as_view(next_page="/")`.
2. `controller/migrations/0002_auto_20180712_0001.py` — see the migration
   landmine below.

With both applied, `manage.py check` is clean (only `models.W042`
`DEFAULT_AUTO_FIELD` warnings) and **all 7 tests pass**. Nothing else in the
codebase needed touching. `django-debug-toolbar` and `psycopg2` are the only
other deps and both have current releases.

> **Do not commit these two changes as part of a feature branch.** They are a
> local convenience for running the code on a modern interpreter. The upstream
> deployment runs Django 2.0.6 / Python 3.7, and any change destined for it must
> run there — so a diff that quietly carries the `urls.py` rewrite or the
> migration edit will break production. Keep them as uncommitted working-tree
> edits, or as a separate branch you never merge into feature work. Modernizing
> upstream is its own proposal, not a side effect. Verify feature work against
> Django 2.0.6 (Path A) before opening a PR.

### The migration landmine (bites any fresh SQLite database)

`controller/migrations/0002` does `RemoveField` on
`insertionsortcontroller.controller_ptr` — the model's only remaining column —
and then `DeleteModel`s it in the same migration. SQLite has no `DROP COLUMN`
in this Django path, so Django rebuilds the table and emits:

```sql
INSERT INTO "new__controller_insertionsortcontroller" () SELECT  FROM "controller_insertionsortcontroller";
```

…an empty column list, which fails with
`django.db.utils.OperationalError: near ")": syntax error`. This presumably
never surfaced upstream because production ran PostgreSQL, where the
`DROP COLUMN` path is used instead.

Consequences: `manage.py migrate` **and** `manage.py test` (which builds a
fresh test DB) both fail on SQLite from a clean slate.

Two fixes:

- **Proper fix (do this if you're modernizing):** delete the two `RemoveField`
  operations from `controller/migrations/0002`. The `DeleteModel` operations
  that follow drop those tables anyway, so the end state is identical. Verified:
  `migrate` succeeds and all 7 tests pass afterward.
- **Workaround (leaves migration history untouched):**
  `manage.py migrate controller 0001 && manage.py migrate --fake controller 0002 && manage.py migrate`.
  This gets a working dev DB but does **not** fix `manage.py test`, which
  always replays migrations from scratch.

### Commands

Run everything from the `charactersorter/` directory (where `manage.py` lives):

```bash
python manage.py check
python manage.py migrate
python manage.py test                    # 7 tests: 6 in controller, 1 in sorterinput
python manage.py test controller         # just the algorithm tests
python manage.py runserver
python manage.py createsuperuser
```

`local_settings.py` points at PostgreSQL (`charactersorter` db, `charsorter`
user). The remote container has the `psql` *client* but no server, so for
local work you'll want a SQLite override — a scratch settings module that
imports `base_settings`, sets `SECRET_KEY`/`DEBUG`/`DATABASES`, and strips
`debug_toolbar` from `INSTALLED_APPS`/`MIDDLEWARE` if it isn't installed.
Don't commit that file.

### Reaching the live site

Deployed behavior is checked against `charsorter.lndyn.com` directly, and no
credentials are needed for the useful part — see "Verifying the live site" in
`ROADMAP.md`. The host is **not** reachable from a cloud session under the
default **Trusted** network access: the egress proxy answers `403` to
`CONNECT`, and `WebFetch` fails the same way, since it is bound by the same
policy. The fix is the environment's own **Custom** allowlist (`*.lndyn.com`,
with the default package-manager list left checked), which applies to a session
already running. Do not try to route around a proxy `403` — it is an egress
policy, not a misconfiguration.

### Linting

`requirements.txt` pins `pylint`, `pylint-django`, `isort`. There is no config
file (`.pylintrc`, `setup.cfg`, `tox.ini` are all absent) and no CI. The
codebase carries inline pylint pragmas (e.g.
`# pylint: disable-msg=too-many-ancestors` at the top of `sorterinput/views.py`),
so pylint was clearly used interactively. Follow the existing style rather than
introducing a formatter — the code is not Black-formatted and reformatting it
would bury real changes in noise.

## Conventions

- **Change scope:** one concern per PR, roughly 100-200 lines. This matters most
  for PRs sent to `jerrywu64/character-sorter`. Upstream took three such PRs in
  a row after seven dormant years, which is the evidence for keeping to it.
  Prefer framework facilities over hand-rolled equivalents. Keep prose out of
  the code: non-obvious architecture belongs in this file or `ROADMAP.md`, not
  in a block comment.
- **Python style:** 4-space indent, ~79-col lines, `"double quotes"` mostly but
  not religiously, `.format()` (not f-strings — this predates the codebase's
  adoption of them). Hanging indents wrap at the opening paren.
- **Docstrings** on non-obvious methods, especially the Glicko math, where they
  reference the Glicko paper. Preserve and extend that habit — the math is
  genuinely hard to read without them.
- **Templates** all `{% extends "base.html" %}` and fill `{% block title %}`,
  `{% block js %}`, `{% block body %}`. Titles follow
  `"<Verb>ing {{ charlist.title }} | Character Sorter"`.
- **Formset pages** (`edit.html`, `editlists.html`) use an identical
  two-form pattern: a `modelformset_factory` formset for editing/deleting
  existing rows, plus a separate `ModelForm` for adding one. Both POST to the
  same URL; the view tries the add form first, then the formset, then
  redirects. If you touch one page, mirror the change in the other.
- **`sorter_extras.get_item`** is a one-line template filter for dict lookup by
  variable key (`annotations|get_item:char.id`), since Django templates can't do
  `dict[key]`.
- **Static/JS** is minimal on purpose: jQuery 3.3.1 from a CDN in `base.html`,
  Plotly from a CDN in `graph.html`, and one small `sort.js` whose only job is
  disabling submit buttons to prevent double-submission. It defers that with
  `setTimeout(..., 0)`: a submit button disabled during its own submit event is
  excluded from the form data set, so disabling it synchronously would drop the
  `sort` value the sort page depends on.
- **CSS** is one hand-written file, `core/static/core/css/style.css`, linked
  from `base.html`. There is no framework, no build step and no second
  stylesheet — keep it that way, and keep the file small enough to read in one
  go. Two patterns in it are load-bearing:
  - The sort page answers a comparison with three `<button type="submit"
    name="sort">` controls (values `1`, `-1`, `0`) styled as cards, rather than
    radios plus a submit. One tap per comparison, and it still works with
    JavaScript off.
  - The formset tables stack into one card per row under `40em`. That relies on
    each `<td>` carrying `data-label="{{ field.label|capfirst }}"`, which the
    `::before` rule renders as the row's field name once the header is hidden.
    `edit.html` and `editlists.html` must stay identical here.
  - Names and fandoms are 200-char user input that need not contain a space, so
    every surface that displays one has to be able to break it. `.container`
    sets `overflow-wrap`, and the sort cards additionally need `min-width: 0`
    on the flex item — a flex item otherwise refuses to shrink below its
    longest word, which puts the whole page into horizontal scroll and undoes
    the viewport tag. Check a long unbroken name at a narrow width before
    changing the sort or view layout.

## Known issues and gotchas

These are real and verified in the current code. Several matter directly if you
build an API for a mobile client.

**Committed secrets.** `local_settings.py` is listed in `.gitignore`
(`*local_settings.py`) but was **never `git rm --cached`'d**, so it is still
tracked and contains a real-looking `SECRET_KEY` and PostgreSQL password. If
production ever shared them, they're compromised. Don't add more secrets to it;
treat rotating them as a prerequisite for any real deployment of this fork.

**Authorization holes (fixed).** `@requires_list_owner` only validates the
`list_id` in the *URL*; object IDs arriving in the POST *body* used to be
unchecked. `undo` now scopes its `SortRecord` lookup to `list_id`, `editlist`
filters `ModifyCharFormset`'s queryset, and the add forms no longer accept
`characterlist`/`owner` from the client — the view sets them from the URL and
`request.user`. `sorterinput/tests.py` carries a regression test per hole.
`sortlist`'s `char1`/`char2` are validated by `register_comparison`, which now
looks characters up through `charlist.character_set` instead of asserting after
a global fetch — the old `assert` vanished under `python -O`, leaking the
victim's name through the "Undo last sort" button. `editcharlists` already
filtered `ModifyCharlistFormset` on POST; earlier notes here claiming otherwise
were wrong.

**Template XSS.** `graph.html` renders `{{ graph_info.characters|safe }}` inside
a `<script>` block. The value is `json.dumps`'d, which escapes quotes but *not*
`</script>` — a character named `</script><script>…` breaks out. The `TODO`
file flags this ("Make sure javascript/html insertion can't occur via character
names lol"). Use `json_script` or escape `<` when fixing.

**`db.sqlite3` is tracked and empty.** It's a 0-byte stub with no tables. It is
not a data source and not a usable database. Consider `git rm --cached`-ing it
and adding it to `.gitignore`.

**Migration history is messy.** `controller/` migrations 0001–0005 build an
`InsertionSortController` model, tear it down, rename `InsertionSortRecord` →
`SortRecord`, then rename its `controller` field → `charlist`. Squashing them
would be reasonable if you're modernizing anyway.

## Git workflow

- Work on a feature branch; push with `git push -u origin <branch-name>` —
  but **run `git remote -v` before every push, and name the remote
  explicitly.** A session sourced from this fork comes up with `origin`
  pointing at **upstream** (`jerrywu64`), not at the fork — verified
  2026-09-02, when this container cloned with exactly that layout. So the
  command above, run unchecked, aims fork work at the maintainer's default
  branch.

  Renaming the remotes does not fix it. The harness re-syncs `origin` to the
  session's source repository, and a rename was observed reverting mid-session
  between one push and the next. Add the fork under its own name and push to
  that name every time:

  ```
  git remote add fork https://github.com/aofei-liu/character-sorter
  git push -u fork <branch-name>
  ```

  A stop hook comparing `origin/<branch>` also reports the fork's whole
  divergent history as "unpushed" whenever `origin` is upstream. That is a
  false positive; pushing to satisfy it is the exact mistake this note exists
  to prevent. GitHub is the last line of defence and it holds — a push to
  `jerrywu64` is refused with a 403 — but do not rely on that.
- Do not open a pull request unless explicitly asked.
- Fork work lands on `main` by PR: branch → PR against `main` → merge. PRs 1-3
  all landed this way. Don't commit to `main` directly.
- `main` has **diverged from upstream** and no longer tracks it. It carries the
  fork-only docs (`CLAUDE.md`, `ROADMAP.md`, `CONTRIBUTING.md`) plus every
  merged change. Anything going upstream needs its own branch cut from
  upstream's head — see below.
- The three `claude/*-upstream` branches are **spent**. Each was cut from
  upstream's head for a cross-fork PR, all three merged as `#10`/`#11`/`#12`,
  and every file they touched is now byte-identical in `main`. They are not
  ancestors of `main` — upstream squashed them and they never shared its
  history — so an ancestry check will call them unmerged. Compare file content,
  not `git merge-base`, before concluding anything is outstanding.

### Opening a PR against upstream

A cross-fork PR to `jerrywu64/character-sorter` **cannot be opened from a Claude
Code session** that was started from this fork. Three independent local gates
stop it, and none of them is GitHub refusing:

1. **Session repo scope.** Only the repos a session was sourced from are in
   scope. `create_pull_request` with owner `jerrywu64` fails the scope check
   before any API call: `Access denied: repository "jerrywu64/character-sorter"
   is not configured for this session.`
2. **`add_repo` cannot attach it.** The obvious fix is blocked twice over.
   First the auto-mode classifier refuses the call (`Blocked by classifier`);
   there is no settings file in this repo or the container, so the classifier
   decides alone. Even with that approved, the tool itself refuses: `cross-tier
   adds are not supported in v1 ... session already has repos from owner(s)
   [aofei-liu]`. The second failure is the durable one — no permission grant
   gets past it.
3. **Sourcing a session from upstream doesn't help either.** The escape that
   error suggests — spawn a new session with `jerrywu64/character-sorter` as
   its initial source — was tried on 2026-09-01 and came back with read-only
   auth; it could not create the PR. The user opened PR #10 by hand from the
   compare link. That attempt cost a full extra session, so don't repeat it.

**Reading upstream is a different matter, and it works.** The GitHub tools are
scope-blocked, but `WebFetch` against `https://github.com/jerrywu64/...` returns
the public repo fine — branch selector, directory listings, commit log. Use it
to confirm what actually landed instead of recording upstream state as hearsay;
that is how the `#10`/`#11`/`#12` merges and the `main` rename were
established. It is read-only, so it opens nothing and routes around nothing.

Verified once (2026-09-01): `list_repos` reports `jerrywu64/character-sorter` as
public with `can_push: true` for the authenticated user `aofei-liu`, so the
account's GitHub access is likely fine. Every denial seen so far is local
tooling, not GitHub — don't report it to the user as "upstream denied access",
and don't retry the blocked call or route around it.

**So the workflow is: prepare and verify everything, then hand the user a
compare link and let them click Create.**

- Cut the branch from upstream's actual head, not from fork `main`, and keep
  fork-only docs (`CLAUDE.md`, `ROADMAP.md`, `CONTRIBUTING.md`) out of it.
- Verify on Django 2.0.6 (Path A) before handing it over, per "Running the code".
- Push the branch to `aofei-liu/character-sorter` — that part works normally.
- Give the user the compare URL, the title, and the body as a file they can
  paste without reformatting:

  ```
  https://github.com/jerrywu64/character-sorter/compare/main...aofei-liu:character-sorter:<branch>?expand=1
  ```

- Tell them the expected file count and commit count so they can spot a wrong
  base on the compare page before submitting.
- Upstream has **no PR template** at `45a897d` (no `.github/`, no `docs/`), so
  there are no headings to mirror.
