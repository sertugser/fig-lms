# Fig LMS

**AI-assisted modernization of selected Moodle subsystems.**

## Scope
| Module | Flows in scope |
|---|---|
| Identity & access | Login, 3 roles (student, teacher, admin), course-level permissions |
| Courses & enrolment | Course creation, teacher assignment, enrolment, course categories |
| Advising | Course prerequisite check (MDL-22648), advisor approval, credit limit |
| Assignments | Assignments with dates, file submission, late-submission rule, drafts and attempts |
| Grading | Grading, weighted mean, students viewing their own grades |
| Study planner (new) | Term-by-term study plan built from prerequisites and credit limits |

Moodle bugs fixed in the new system: MDL-87490, MDL-85503.

## Getting started
- **Tasks:** all work is tracked on the [project board](https://github.com/users/sertugser/projects/3) and grouped by [milestone](https://github.com/sertugser/fig-lms/milestones). Filter issues with `assignee:<your-username>`. If an issue is temporarily assigned to someone else, the real owner is in the **Owner** row at the top of the issue.
- **Moodle source:** we analyse Moodle 5.3.1. Paths in issues are relative to its `public/` folder.
  ```bash
  git clone --depth 1 -b v5.3.1 https://github.com/moodle/moodle.git   # same version as infra/
  ```
- **How to contribute:** see [CONTRIBUTING.md](CONTRIBUTING.md).

## Quick start
Requires Docker (Docker Desktop on macOS/Windows). Run from the repository root:
```bash
cp infra/.env.example infra/.env                # once; edit the passwords if you like
docker compose -f infra/docker-compose.yml up -d  # Moodle 5.3.1 + PostgreSQL 17
./infra/sample-data/load.sh                     # sample courses, users, assignment, grades
```
- Moodle: http://localhost:8000. Log in as `admin` with `MOODLE_ADMIN_PASSWORD` from `infra/.env`.
- The first start builds the image and installs Moodle (about 5 minutes). Wait until `docker compose -f infra/docker-compose.yml logs moodle` shows `MOODLE_WS_TOKEN=...`.
- REST token for the golden master tests: see [infra/README.md](infra/README.md#rest-api-token).

Details, sample data contents and troubleshooting: [infra/README.md](infra/README.md).

### Backend (new system)
Requires Java 21 (Maven is downloaded by the wrapper):
```bash
cd backend
./mvnw verify      # build, tests and the module boundary check (ModularityTest)
```
Windows: use `mvnw.cmd verify`. CI runs the same command on every pull request. The backend gets its own PostgreSQL service in the Compose file once the first module stores data (#6).

## Repository layout
```
backend/             Spring Boot application (modules: identity, course, advising, assignment, grading, planning)
frontend/            React UI
tests/golden-master/ Tests that feed the same inputs to Moodle and the new system and compare results
tests/e2e/           Playwright tests (including click-count measurements)
infra/               Docker Compose, sample data, data migration script
docs/srs/            Software requirements specification and traceability matrix
docs/adr/            Architecture decision records
docs/ai-log/         AI usage log
docs/weekly/         Weekly progress reports
docs/methodology/    Modernization methodology
```

## License
GPL-3.0-or-later. See [LICENSE](LICENSE). Moodle is licensed under GPL-3.0-or-later, and the business rules here are derived from its source code, so the same license applies.
