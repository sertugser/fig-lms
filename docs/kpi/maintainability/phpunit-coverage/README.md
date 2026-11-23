# Test coverage baseline (MAINT-05)

Measures the line coverage of Moodle's own PHPUnit tests for the folders of each module (same `MODULES` as [measure.py](../measure.py)).

## What is here
| File | Purpose |
|---|---|
| `infra/moodle/Dockerfile.phpunit` | Test variant of the Moodle image: PCOV switched on, PHPUnit and the other dev dependencies installed, PHPUnit uses table prefix `phpu_` and its own data folder |
| `infra/docker-compose.phpunit.yml` | Adds the `moodle-phpunit` container next to the normal stack (same PostgreSQL server) |
| `run_coverage.py` | Runs the tests of one module with coverage in the container |
| `summarize_coverage.py` | Computes the coverage per module from the clover reports and writes `results/phpunit/coverage.csv` |

## Steps
Run from the repository root. `infra/.env` must exist (see [infra/README.md](../../../../infra/README.md)). The normal Moodle must be running, because both containers share its database server.

```bash
# 1. Build and start the test container (a few minutes the first time)
docker compose -f infra/docker-compose.yml -f infra/docker-compose.phpunit.yml up -d --build moodle-phpunit

# 2. Initialise PHPUnit once (creates the phpu_ tables and its data folder; a few minutes). Run as root: the Moodle code folder belongs to root.
docker compose -f infra/docker-compose.yml -f infra/docker-compose.phpunit.yml exec -T -u root -e COMPOSER_ALLOW_SUPERUSER=1 moodle-phpunit php public/admin/tool/phpunit/cli/init.php

# 3. Run one module (start with a small one: advising), then the others
python docs/kpi/maintainability/phpunit-coverage/run_coverage.py advising
python docs/kpi/maintainability/phpunit-coverage/run_coverage.py planning   # also: grading, assignment, identity, course

# 4. Compute the coverage per module (needs a clone of Moodle v5.3.1 and `pip install lizard`)
python docs/kpi/maintainability/phpunit-coverage/summarize_coverage.py <moodle>/public

# 5. Compress the clover reports before committing (they are several MB each); step 4 reads both .xml and .xml.gz
gzip -9 -n docs/kpi/maintainability/results/phpunit/*-clover.xml
```
On Windows with Git Bash, put `MSYS_NO_PATHCONV=1` in front of the `docker compose ... exec` command, otherwise paths like `public/admin/...` are rewritten.

The PHPUnit test data lives in the `phpu_` tables and in the Docker volume `phpunit-dataroot`. The normal Moodle (`mdl_` tables, volume `moodledata`) is never used by these tests.
To remove the test setup without touching the normal Moodle: `docker compose -f infra/docker-compose.yml -f infra/docker-compose.phpunit.yml exec -T -u root moodle-phpunit php public/admin/tool/phpunit/cli/util.php --drop`, then stop the container and delete the volume (`docker volume rm fig-lms_phpunit-dataroot`).
Do not use `down -v` for this: it also deletes the normal Moodle database and data.

## How the number is defined
- **Tests run:** every `*_test.php` in the module's folders, plus the single test files listed in `MODULES` (for example `lib/tests/completionlib_test.php`). Tests that need an external service (LDAP, a mail server, ...) are skipped by Moodle itself; skipped tests are counted in `coverage.csv`.
- **Line coverage** = covered executable lines / executable lines, as reported by PHPUnit with PCOV, over the *production PHP files* of the module. Files are selected exactly as in `measure.py` (no tests, no language packs, no third-party code). A file that no test loads still counts with all its executable lines as not covered.
- Only unit tests (PHPUnit) are run. Behat (browser) tests and JavaScript tests are not included, so the numbers are a lower bound of what the full Moodle test suite covers.
- Coverage is measured on code of the *module's* folders only, but the tests of the module also run code from core (`lib/`, `mod/`, ...); that code is outside the denominator on purpose.
