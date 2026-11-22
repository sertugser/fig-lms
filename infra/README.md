# Local Moodle 5.3.1 (infra)

One command starts a Moodle 5.3.1 that everyone on the team uses for bug reproduction, rule checks, KPI baselines and golden master tests.

## Services
| Service | What it is | Port |
|---|---|---|
| `moodle` | Moodle 5.3.1 (tag `v5.3.1`) on PHP 8.3 + Apache, image `moodlehq/moodle-php-apache:8.3` | `8000` → 80 |
| `moodle-db` | PostgreSQL 17 | not published |
| `moodle-cron` | Same image, runs `admin/cli/cron.php` every 60 s | - |

On the first start, the `moodle` container installs Moodle (`admin/cli/install_database.php`). On every start it runs [`moodle/fig-ws-setup.php`](moodle/fig-ws-setup.php), which turns on web services and REST and makes sure the REST token exists. Moodle data lives in the Docker volumes `moodle-db` and `moodledata`.

## Start
From the repository root:
```bash
cp infra/.env.example infra/.env                 # once
docker compose -f infra/docker-compose.yml up -d
docker compose -f infra/docker-compose.yml logs -f moodle   # wait for "MOODLE_WS_TOKEN=...", then Ctrl+C
```
Open http://localhost:8000. Admin login: `admin` / `MOODLE_ADMIN_PASSWORD` from `infra/.env`.

The first start takes about 5 minutes: building the image takes 1–2 minutes, then installing Moodle takes 2–4 minutes. Later starts take a few seconds.

## Sample data
```bash
./infra/sample-data/load.sh
# Windows (PowerShell), or without bash:
docker compose -f infra/docker-compose.yml exec -T -u www-data moodle php /opt/fig/sample-data/load.php
```
Running it again does nothing (it checks for category `FIG-ENG`).

| What | Details |
|---|---|
| Category | Engineering (`FIG-ENG`) |
| Courses | `CS101` Introduction to Programming, `CS201` Data Structures, `MATH101` Discrete Mathematics. Term: 28 Sep 2026 to 22 Jan 2027. |
| Prerequisite | `CS201` has two course completion criteria (all required): "completion of other courses: `CS101`" and "marked complete by teacher" (the second keeps CS201 from completing automatically). `CS101` is complete when *Assignment 1* is passed (grade ≥ 50). |
| Teachers (`editingteacher`) | `teacher1` in CS101 and CS201, `teacher2` in MATH101 |
| Students (`student`) | `student1`–`student5` are all in CS101. `student1`–`student3` are also in CS201 and `student4`–`student5` in MATH101. |
| Assignment | *Assignment 1* in CS101: max grade 100, pass grade 50, online text + 1 file, due 15 Nov 2026, cut-off 22 Nov 2026 |
| Submissions / grades | student1 85, student2 72, student3 40, student4 submitted but not graded, student5 did not submit |
| Completion | student1 and student2 completed CS101. student3 did not, yet is enrolled in CS201, because Moodle does not block enrolment on prerequisites ([MDL-22648](https://moodle.atlassian.net/browse/MDL-22648)). |

All sample users have the password `SAMPLE_USER_PASSWORD` from `infra/.env`.

## REST API token
Web services, the REST protocol and an external service **`fig_golden_master`** are set up automatically. The service holds only read functions: courses, categories, enrolments, users, completion, assignments and grades. The full list is in [`moodle/fig-ws-setup.php`](moodle/fig-ws-setup.php).

The token is a permanent token for the `admin` user.
- **Fixed token (recommended):** put a value in `infra/.env`, e.g. `MOODLE_WS_TOKEN=$(openssl rand -hex 16)`, then run `docker compose -f infra/docker-compose.yml up -d` again. The container sets the token to that value on every start.
- **Generated token:** if `MOODLE_WS_TOKEN` is empty, Moodle generates one. Read it with:
  ```bash
  docker compose -f infra/docker-compose.yml logs moodle | grep MOODLE_WS_TOKEN
  # or
  docker compose -f infra/docker-compose.yml exec -u www-data moodle php /opt/fig/fig-ws-setup.php
  ```
- In the UI: *Site administration → Server → Web services → Manage tokens*.

Test call:
```bash
TOKEN=<your token>
curl -s "http://localhost:8000/webservice/rest/server.php?wstoken=$TOKEN&wsfunction=core_course_get_courses&moodlewsrestformat=json"
```
It returns a JSON array of courses: the site course with `id` 1, plus CS101, CS201 and MATH101 once the sample data is loaded.

Note for the harness (#12): some functions only return data for courses the token user is enrolled in. For example, `mod_assign_get_assignments` returns no courses for `admin`, with the warning "User is not enrolled". Functions that take an id, such as `mod_assign_get_grades` or `core_completion_get_course_completion_status`, work.

To give the harness another function, add it to `FIG_FUNCTIONS` in `moodle/fig-ws-setup.php` and rebuild: `docker compose -f infra/docker-compose.yml up -d --build`.

## Common tasks
```bash
docker compose -f infra/docker-compose.yml stop             # stop, keep data
docker compose -f infra/docker-compose.yml down -v          # delete everything (DB + moodledata) and start fresh
docker compose -f infra/docker-compose.yml exec -u www-data moodle php admin/cli/purge_caches.php
docker compose -f infra/docker-compose.yml exec -u www-data moodle php admin/cli/cron.php
```
The Moodle code is inside the image at `/var/www/moodle`. The web root is `/var/www/moodle/public`, and `admin/cli` is under `/var/www/moodle`. To read the source on your machine, see "Moodle source" in the main README.

## Troubleshooting
- **`required variable MOODLE_DB_PASSWORD is missing`**: you have not created `infra/.env` yet (`cp infra/.env.example infra/.env`).
- **Port 8000 is taken**: change both `MOODLE_PORT` and `MOODLE_WWWROOT` in `infra/.env`, then run `down -v` and `up -d`. The URL is stored at install time.
- **Changed the DB password after the first start**: the database keeps the old one. Run `down -v` and start again.
- **Page shows "Installation" or errors right after `up -d`**: the install is still running. Watch `logs -f moodle`.
