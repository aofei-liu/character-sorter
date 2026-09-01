# CLAUDE.md

Guidance for AI assistants working in this repository.

## What this is

A Django web app for **ranking arbitrary lists of characters through pairwise
comparison**. You create a "character list", add characters to it, then
repeatedly answer "which of these two is better?" until the app can produce a
ranked order.

This repo is a **fork of [jerrywu64/character-sorter](https://github.com/jerrywu64/character-sorter)**,
which is deployed at <https://charsorter.lndyn.com/>. As of this writing the
fork's `master` is at the same commit as upstream `master` (`45a897d`) — there
is no divergence yet. Upstream has been dormant since 2018.

The fork exists to build a mobile-friendly version of the UI, which must read
and write the **live database behind `charsorter.lndyn.com`** rather than a
local copy. That constrains what code may be written here — most importantly,
anything destined for the upstream deployment must run on Django 2.0.6 /
Python 3.7. Planning, sequencing and rationale live in
[`ROADMAP.md`](ROADMAP.md); this file covers only the code as it stands.

## Repository layout

```
requirements.txt              Pinned 2018-era deps (see "Running the code")
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

**Path B — modernize (recommended for the fork's goals).** Verified working:
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

### Linting

`requirements.txt` pins `pylint`, `pylint-django`, `isort`. There is no config
file (`.pylintrc`, `setup.cfg`, `tox.ini` are all absent) and no CI. The
codebase carries inline pylint pragmas (e.g.
`# pylint: disable-msg=too-many-ancestors` at the top of `sorterinput/views.py`),
so pylint was clearly used interactively. Follow the existing style rather than
introducing a formatter — the code is not Black-formatted and reformatting it
would bury real changes in noise.

## Conventions

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
  Plotly from a CDN in `graph.html`, and one 12-line `sort.js` whose only job is
  disabling submit buttons to prevent double-submission. There is **no CSS at
  all** and no stylesheet file anywhere in the repo.

## Known issues and gotchas

These are real and verified in the current code. Several matter directly if you
build an API for a mobile client.

**Committed secrets.** `local_settings.py` is listed in `.gitignore`
(`*local_settings.py`) but was **never `git rm --cached`'d**, so it is still
tracked and contains a real-looking `SECRET_KEY` and PostgreSQL password. If
production ever shared them, they're compromised. Don't add more secrets to it;
treat rotating them as a prerequisite for any real deployment of this fork.

**Authorization holes.** `@requires_list_owner` only validates the `list_id` in
the *URL*. Object IDs arriving in the POST *body* are unchecked:

- `undo` does `get_object_or_404(SortRecord, pk=request.POST["last"])` with no
  check that the record belongs to `list_id` — any authenticated user who owns
  any list can delete any `SortRecord` by ID.
- `editlist` binds `ModifyCharFormset(request.POST)` without a queryset filter,
  and `AddCharForm` takes `characterlist` from a hidden input — so a user can
  edit/delete characters in, or add characters to, someone else's list.
- `editcharlists` has the same shape: unfiltered `ModifyCharlistFormset` on
  POST, and `owner` as a hidden input on `AddCharlistForm`.

The README's "this server almost certainly has security vulnerabilities" is
accurate. **Fix these before exposing anything new**, especially a JSON API,
where they'd be trivially exploitable.

**Template XSS.** `graph.html` renders `{{ graph_info.characters|safe }}` inside
a `<script>` block. The value is `json.dumps`'d, which escapes quotes but *not*
`</script>` — a character named `</script><script>…` breaks out. The `TODO`
file flags this ("Make sure javascript/html insertion can't occur via character
names lol"). Use `json_script` or escape `<` when fixing.

**`db.sqlite3` is tracked and empty.** It's a 0-byte stub with no tables. It is
not a data source and not a usable database. Consider `git rm --cached`-ing it
and adding it to `.gitignore`.

**No `viewport` meta tag.** `core/templates/base.html` has no `<!DOCTYPE>`, no
`<meta charset>`, and no `<meta name="viewport">`. On a phone this means the
browser renders at desktop width and scales down. This is the single biggest
cause of the site being unusable on mobile, and the cheapest thing to fix.

**Deprecated markup.** `sort.html` uses `<font size="0">` tags (the `TODO` file
wants these gone: "use css, get rid of font tag lol").

**Migration history is messy.** `controller/` migrations 0001–0005 build an
`InsertionSortController` model, tear it down, rename `InsertionSortRecord` →
`SortRecord`, then rename its `controller` field → `charlist`. Squashing them
would be reasonable if you're modernizing anyway.

## Git workflow

- Development for this task happens on `claude/claude-md-mobile-app-b1obdp`.
- Push with `git push -u origin <branch-name>`.
- Do not open a pull request unless explicitly asked.
- `master` here tracks upstream. Keep fork-specific work on feature branches so
  upstream can still be merged cleanly if it ever revives.
