# Fig LMS

**AI-assisted modernization of selected Moodle subsystems.**

> New to the team? Start with the [first day guide](docs/FIRST-DAY.md).

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

## Quick start
```bash
docker compose -f infra/docker-compose.yml up -d   # Moodle 5.3.1 + new system + PostgreSQL
```

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
