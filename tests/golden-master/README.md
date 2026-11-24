# Golden master tests

Send the same input to Moodle and to the new system, then compare the results. This folder holds the Moodle side:
a small client that reads from the Docker Moodle, the snapshots it saves, and the `assertMatchesGolden(...)` helper that
compares the new system's response with a snapshot.

```
src/main/java/com/figlms/goldenmaster/
  MoodleClient.java     REST client (token from infra/.env or the environment)
  MoodleExpected.java   raw Moodle response -> simple "expected result" JSON (no ids, no timestamps, no HTML)
  Snapshots.java        reads/writes snapshots/*.json (sorted keys, so re-fetching gives no git noise)
  JsonDiff.java         lists every difference with its path
  GoldenAssert.java     assertMatchesGolden(...)
  FetchSnapshots.java   the "fetch" command
  FigBackendClient.java REST client for the new system (FIG_BACKEND_URL, SAMPLE_USER_PASSWORD)
src/test/java/...       tests of the helper with fake data (no Moodle needed)
  advising/PrerequisiteGoldenMasterTest.java   #13: prerequisite check, Moodle vs new system
run-prerequisite-golden-master.sh            runs #13 in one command
snapshots/              expected results saved from Moodle 5.3.1 + the sample data of infra/
```

## Requirements
Java 21 (the Maven wrapper downloads Maven). Run all commands in `tests/golden-master/`. On Windows use `mvnw.cmd` instead of `./mvnw`.

## 1. Run the tests (no Moodle needed)
```bash
./mvnw test
```
This tests the helper itself with fake data: one matching and one mismatching comparison, the diff output, the snapshot files and the
converters.

## 2. Fetch snapshots from Moodle
Start Moodle and load the sample data first ([infra/README.md](../../infra/README.md)), and make sure `MOODLE_WS_TOKEN` is set in `infra/.env`.

Then let Moodle calculate course completion once. Right after the sample data is loaded, `core_completion_get_course_completion_status` still reports the CS201 criterion "Completion of other courses: CS101" as not complete for `student1` and `student2`, although both completed CS101. Moodle updates it in the scheduled task `completion_regular_task`. The `moodle-cron` container runs it within about a minute; to be sure, run it by hand (from the repository root):
```bash
docker compose -f infra/docker-compose.yml exec -T -u www-data moodle php admin/cli/scheduled_task.php --execute='\core\task\completion_regular_task'
```
Then fetch (in `tests/golden-master/`):
```bash
./mvnw compile exec:java
```
The client reads `MOODLE_URL` (default `http://localhost:8000`) and `MOODLE_WS_TOKEN` from the environment or from `infra/.env`.
It calls these web service functions and writes the results to `snapshots/`:

| Moodle function | Snapshot |
|---|---|
| `core_course_get_courses` | `courses.json` |
| `core_enrol_get_enrolled_users` (per course) | `enrolled-<course>.json` |
| `core_completion_get_course_completion_status` (per enrolled student) | `completion-<course>-<username>.json` |

Snapshots are committed, so tests also run where Moodle is not running (for example in CI). Fetch again only when the sample data changes,
and review the git diff of `snapshots/` before committing. If Moodle answers a call with an error, the error code is saved as the
expected result (`{"error": "..."}`), because that is Moodle's behaviour too.

To add a function: add a converter to `MoodleExpected`, call it from `FetchSnapshots`, and add the function to
`FIG_FUNCTIONS` in `infra/moodle/fig-ws-setup.php` if it is not there yet.

## 3. Use the helper in a golden master test
```java
JsonNode actual = ...;   // response of the new system, as JSON
GoldenAssert.assertMatchesGolden("enrolled-cs101", actual);
```
On a difference the test fails and prints each one with its path:
```
New system differs from Moodle golden master 'enrolled-cs101' in 2 place(s):
  - $.users[1].roles[0]: expected "editingteacher" but was "student"
  - $.users: expected 4 item(s) but was 3
```
If the snapshot does not exist, the test fails and points to this README. Snapshots are read from `snapshots/` in the working directory;
set `-Dgolden.snapshots=<folder>` to use another folder.

Shape of an expected result: courses are identified by `shortname` and users by `username` (never by database id), lists are sorted,
and volatile values (last access, times of modification) are left out. The new system's API must return, or be mapped to, this shape.
Agree on any change of the format with the other golden master authors (#13).

## 4. Golden master #13: prerequisite check, Moodle vs new system
`advising/PrerequisiteGoldenMasterTest` sends the same input to Moodle and to `GET /api/advising/prerequisite-check` and
compares the results. Moodle cannot answer "may student X enrol in CS201?" (MDL-22648), so the Moodle fact is the student's
completion of the prerequisite CS101, and the new system must say `allowed = completed` and `missingPrerequisites = ["CS101"]`
when not allowed (mapping: [advising SRS](../../docs/srs/modules/advising.md#prerequisite-rule-req-adv-08--req-adv-09-used-by-13)).

| Scenario | Input | Moodle | New system |
|---|---|---|---|
| Completed | student1 → CS201 (CS101 passed, 85) | CS101 completed | allowed |
| Not completed | student5 → CS201 (CS101 not submitted) | not completed | not allowed, missing CS101 |
| Boundary | student3 → CS201 (CS101 failed, 40 < pass 50); student4 → CS201 (submitted, not graded) | not completed | not allowed, missing CS101 |
| Unauthorized | student3 asks about student1 | `cannotviewreport` (rule from the Moodle source, see below) | 403 |

One command (Java 21 and Docker; Windows: Git Bash or WSL):
```bash
./run-prerequisite-golden-master.sh
```
It starts a throwaway PostgreSQL 17 in Docker, builds and starts the backend on port 18080 with the sample data, runs the test,
prints the table below and stops everything again. With your own PostgreSQL, set `POSTGRES_HOST`, `POSTGRES_PORT`,
`POSTGRES_DB`, `POSTGRES_USER` and `POSTGRES_PASSWORD` and no container is started. If the backend already runs, `./mvnw test`
is enough (`FIG_BACKEND_URL`, default `http://localhost:8080`, and its `SAMPLE_USER_PASSWORD` from the environment, `.env` or
`infra/.env`); without a running backend the test is skipped, or fails with `-Dgolden.requireBackend=true`.

The Moodle side is read live when Moodle runs and `MOODLE_WS_TOKEN` is set, otherwise from `snapshots/completion-cs101-*.json`
(`-Dgolden.moodle=snapshots` forces the snapshots). The report says which one was used:
```
Scenario       Input                                              Moodle 5.3.1                             New system                              Result
Completed      student1 -> CS201 (CS101 passed with 85)           CS101 completed=true [snapshot]          allowed=true (PREREQUISITES_COMPLETED)  SAME
Not completed  student5 -> CS201 (CS101 not submitted)            CS101 completed=false [snapshot]         allowed=false (PREREQUISITES_MISSING)   SAME
Boundary       student3 -> CS201 (CS101 attempted, 40 < pass 50)  CS101 completed=false [snapshot]         allowed=false (PREREQUISITES_MISSING)   SAME
Boundary       student4 -> CS201 (CS101 submitted, not graded)    CS101 completed=false [snapshot]         allowed=false (PREREQUISITES_MISSING)   SAME
Unauthorized   student3 asks about student1                       denied (cannotviewreport) [Moodle rule]  denied (403)                            SAME

5/5 scenarios give the same result in Moodle and the new system.
```
On a difference the scenario fails and lists each one, for example `$.allowed: expected false but was true`. The table is also
written to `target/golden-master/prerequisite.md`; CI shows it in the job summary.

Limits:
- **Unauthorized** is compared with the documented Moodle rule (`completion_can_view_data()` in
  `completion/classes/external.php`), not a live call: the harness token belongs to `admin`, and the sample students have
  no web service token.
- **Boundary.** Issue #13 suggested "completed on the last allowed day". The sample data has no completion deadline, so the
  boundary cases are the ones in the SRS: failed below the pass grade, and submitted but not graded.
- The reason field (`PREREQUISITES_COMPLETED`, ...) exists only in the new system and is shown, not compared.
- Intentional difference (REQ-ADV-08): Moodle still lets student3 enrol in CS201; the new system says no.
