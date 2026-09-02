# Working on this fork

Fork-only file, like [`ROADMAP.md`](ROADMAP.md). Never include it in a PR sent to
`jerrywu64/character-sorter`.

See [`CLAUDE.md`](CLAUDE.md) for what the code is and how it runs, and
[`ROADMAP.md`](ROADMAP.md) for what to build next.

## Change scope

- **One concern per PR, roughly 100-200 lines.** If a change does two things,
  it is two PRs. This matters most upstream, and it worked: three small
  single-purpose PRs merged there in a row after seven dormant years. Assume
  about one round of questions per PR, and spend it well.
- **Prefer framework facilities to hand-rolled equivalents.** Django already
  does most of this. A `queryset=` argument beats a manual ownership loop.
- **Remove the attack surface rather than validating it.** PR 1 dropped the
  hidden `characterlist`/`owner` fields instead of checking them: nothing for a
  client to submit, and no new invalid-form path through the view.
- **Keep prose out of the code.** Non-obvious architecture belongs in
  `CLAUDE.md`, `ROADMAP.md`, or here — not in a block comment. Short docstrings
  explaining a non-obvious property at the point it is enforced are fine.

## Tests

Drawn from the PR 1 review, where the first version of the tests passed under
mutations that broke the feature outright.

- **Every negative assertion needs a positive control in the same request.** A
  security test that only asserts "the victim's object did not change" passes
  just as happily when the fix over-restricts and the feature is dead. Drive the
  legitimate path alongside the attack: POST the victim's row at `form-0` *and*
  your own at `form-1`, then assert the first is untouched and the second saved.
- **Mutation-test the controls.** Break the fix in the over-restrictive
  direction — `Model.objects.none()`, a filter scoped to the wrong column — and
  confirm the suite goes red. If it stays green, the test is decorative.
- **Verify tests fail for the right reason.** Run them against the pre-fix code
  and read the failure. A test that fails because the view 500s, or because
  `refresh_from_db()` raised on a deleted row, is not asserting what it claims.
  Django's test client re-raises view exceptions, so a crash shows as an *error*
  rather than a failure — that distinction is the tell.
- **One test per mechanism, not per symptom.** Two tests differing only by
  `form-0-DELETE` bottom out in the same two lines of Django and are really
  asserting "ModelFormSet respects its queryset" twice. Group by the thing that
  can break: a queryset filter, a `save(commit=False)`, a lookup filter.
- **Don't test code the PR didn't touch.** A test that passes identically with
  and without the diff is dead weight, and worse if its name implies the PR
  closed a hole it did not. Keep it only as an explicitly-labelled guard.
- **Don't hide the payload under test in a helper.** If the tampered ID is the
  point of the test, it belongs at the call site.
- **Watch the test-to-source ratio.** 86 lines of test for 27 lines of fix is a
  reviewability cost on a maintainer who may never reply.

## Verifying against Django 2.0.6

Anything destined for upstream must run on **Django 2.0.6 / Python 3.7**. Recent
containers have neither Python 3.7 nor a Docker daemon, so the practical
approach is real Django 2.0.6 on Python 3.10 with two shims. Nothing here is
committed — keep it outside the repo (a scratch dir on `PYTHONPATH`).

```bash
python3.10 -m venv dj20 && ./dj20/bin/pip install "Django==2.0.6" numpy scipy requests
```

`sitecustomize.py` — Python 3.10 removed the `collections` ABC aliases that
Django 2.0 still uses:

```python
import collections, collections.abc
for _n in ("Iterator", "Iterable", "Mapping", "MutableMapping", "Sequence",
           "MutableSequence", "Callable", "Set", "MutableSet", "Hashable",
           "Sized", "Container"):
    if not hasattr(collections, _n):
        setattr(collections, _n, getattr(collections.abc, _n))
```

`dj20_sqlite/base.py` — Django only started emitting
`PRAGMA legacy_alter_table = ON` in 2.1.5, so on SQLite >= 3.26 its table-rebuild
path leaves `..._old` dangling:

```python
from django.db.backends.sqlite3 import base

class DatabaseWrapper(base.DatabaseWrapper):
    def get_new_connection(self, conn_params):
        conn = super().get_new_connection(conn_params)
        conn.cursor().execute("PRAGMA legacy_alter_table = ON")
        return conn
```

`dj20_settings.py` — note `MIGRATION_MODULES`, which sidesteps the
`controller/migrations/0002` landmine documented in `CLAUDE.md` without editing
migration history:

```python
from charactersorter.base_settings import *  # noqa

SECRET_KEY = "test-only-not-a-secret"
DEBUG = False
ALLOWED_HOSTS = ["testserver"]
DATABASES = {"default": {"ENGINE": "dj20_sqlite", "NAME": ":memory:"}}
INSTALLED_APPS = [a for a in INSTALLED_APPS if a != "debug_toolbar"]
MIDDLEWARE = [m for m in MIDDLEWARE if "debug_toolbar" not in m]
MIGRATION_MODULES = {"controller": None}
LOGGING = get_logging("/tmp/charsorter-test.log")
```

Then, from `charactersorter/`:

```bash
PYTHONPATH=$SCRATCH $SCRATCH/dj20/bin/python manage.py test --settings=dj20_settings
PYTHONPATH=$SCRATCH $SCRATCH/dj20/bin/python -O manage.py test --settings=dj20_settings
```

Run both. The `-O` pass matters: the codebase has used bare `assert` for
security checks, and `-O` strips them.

## Sending a PR upstream

The fork's `main` carries `CLAUDE.md`, `ROADMAP.md`, and this file, so a
branch cut from it cannot go upstream as-is. Cut a second branch from upstream's
actual head and cherry-pick only the source commits:

```bash
git checkout -b <name>-upstream 45a897d   # or current upstream main
git cherry-pick <source commits>
git diff --name-only 45a897d HEAD          # must list only charactersorter/ files
```

Keep the two lines of work in separate commits from the start — one for source,
one for fork docs — so the cherry-pick is clean.
