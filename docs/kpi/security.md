# Security KPIs: Moodle baseline

Baseline for Moodle 5.3.1 (`v5.3.1`, commit `cfe123f2`), the version used by `infra/`. Measurements were made against the local Docker Compose instance and its sample data on 2026-11-22. These are development baselines, not a production security certification.

## OWASP areas in scope

- **A01: Broken Access Control** — course, activity, grade, enrolment, and role access across direct URLs and changed IDs.
- **A03: Injection** — a harmless SQL metacharacter in course search; this is a smoke check, not a penetration test.
- **A05: Security Misconfiguration** — password, account lockout, and session settings.
- **A07: Identification and Authentication Failures** — valid/invalid credentials and account lockout behavior.

## Checks and proposed targets

Targets are proposals for the new system. “Allowed” below is intentional only for the role/action pair shown.

| KPI | OWASP | Definition / unit | Moodle baseline | Proposed target | How measured |
|---|---|---|---|---|---|
| SEC-01 | A01 | Cross-course direct URL access / allow or deny | A student not enrolled in CS201 who requested `/course/view.php?id=3` was redirected to `/enrol/index.php?id=3`; course content was not served. | Deny 100% of unauthorised course requests, independent of URL/ID. | Log in as `student5` and request the CS201 course URL directly; verify only the enrolment page is shown. |
| SEC-02 | A01 | Enrolled course access / allow or deny | `student1` opened enrolled CS101; HTTP 200 and course title displayed. | Allow enrolled users and deny non-enrolled users in 100% of checks. | Log in as `student1`; request `/course/view.php?id=2`, then compare with SEC-01. |
| SEC-03 | A01 | Student assignment grading access / allow or deny | `student1` could view the assignment but not its grading page; the grading request returned HTTP 404 and `mod/assign:grade` was false. | Deny 100% of student grading attempts, including direct requests. | Log in as `student1`; request `/mod/assign/view.php?id=1&action=grading`; confirm denial and verify the capability with `measure.php`. |
| SEC-04 | A01 | Teacher/manager grading access / allow or deny | `teacher1` and the same normal account temporarily assigned the `manager` role in CS101 opened the grading URL; both returned HTTP 200 with the submissions page. The original teacher role was restored. | Allow only assigned teachers/managers in 100% of checks. | Log in as `teacher1` and as the temporary course manager; request the SEC-03 URL and verify `mod/assign:grade`. |
| SEC-05 | A01 | Assignment creation / role-capability result | `moodle/course:manageactivities`: student **false**, editing teacher **true**, course manager **true**, site admin **true**. | Match the approved role matrix in 100% of checks. | Run [`measure.php`](security/measure.php) after sample-data load; the manager role was checked separately with a temporary course role assignment. |
| SEC-06 | A01 | Manual enrolment / role-capability result | `enrol/manual:enrol`: student **false**, editing teacher **true**, course manager **true**, site admin **true**. | Match the approved role matrix in 100% of checks. | Run [`measure.php`](security/measure.php); the manager role was checked separately with a temporary course role assignment. |
| SEC-07 | A07 | Invalid-password authentication / authenticated session count | A wrong password for `student1` did not create an authenticated session. | Zero authenticated sessions for invalid credentials. | Submit one wrong password through the login form and request `/my/` in the same cookie session; it returns to login. |
| SEC-08 | A07 | Failed-login lockout / attempts and seconds | Lockout threshold **0 (disabled)**; configured window **1,800 s** and duration **1,800 s**. | Enable throttling; proposed threshold 5 failures per 15 min and a 15 min lockout, with recovery and source-level throttling to limit account-lockout abuse. | Read the live `$CFG` values via `measure.php`; test the configured threshold only on disposable accounts. |
| SEC-09 | A07 | Password policy / length and character classes | Policy enabled; minimum **8** characters, at least **1** digit, lowercase, uppercase, and non-alphanumeric character. `passwordpolicycheckonlogin` is **0**. | Minimum 12 characters; enforce policy for new/reset passwords and review legacy accounts without forcing unsafe shared-password changes. | Read the live `$CFG` values via `measure.php`; defaults are in `admin/settings/security.php`. |
| SEC-10 | A05 | Inactive session timeout / seconds | **28,800 s (8 h)**. | At most **7,200 s (2 h)** for an inactive authenticated session. | Read `$CFG->sessiontimeout` via `measure.php`; Moodle's setting default is in `admin/settings/server.php`. |
| SEC-11 | A03 | SQL error disclosure from search input / HTTP status and database-error count | Authenticated course search with the literal input `' OR '1'='1` returned HTTP 200 and a normal “Search results” page; no SQL/database error was present. | Zero SQL errors and no results beyond those permitted by the user's role. | Log in as a sample student and repeat the same URL-encoded search input; inspect status, result set, and response for database errors. |

The built-in Moodle `admin` account is a site administrator, not an ordinary `manager` role assignment. Its positive capability results are a bootstrap-admin control only. The normal manager role was independently checked by temporarily changing `teacher1`'s CS101 role assignment to `manager`, logging in, opening the course and grading page, then restoring the original `editingteacher` assignment. This tests a course-scoped manager, not a site-wide manager.

## Repeat the measurements

From the repository root, follow the start and sample-data steps in [`infra/README.md`](../../infra/README.md). Do not commit `infra/.env`.

1. Start Moodle with `docker compose -f infra/docker-compose.yml up -d`, wait for the Moodle health check, and load data with `docker compose -f infra/docker-compose.yml exec -T -u www-data moodle php /opt/fig/sample-data/load.php`.
2. Log in using `admin` / `MOODLE_ADMIN_PASSWORD`, `teacher1` / `SAMPLE_USER_PASSWORD`, and `student1` / `SAMPLE_USER_PASSWORD` from the local `infra/.env`.
3. Check SEC-01 through SEC-04 with the exact URLs in the table. The sample IDs are course CS101 `2`, course CS201 `3`, and the CS101 assignment module `1`; confirm them against the UI if the sample data changes.
4. Copy and run the capability/configuration probe:
   ```powershell
   docker compose -f infra/docker-compose.yml cp docs/kpi/security/measure.php moodle:/tmp/security-measure.php
   docker compose -f infra/docker-compose.yml exec -T -u www-data moodle php /tmp/security-measure.php
   ```
5. Repeat SEC-07 and SEC-11 in a fresh browser session. Do not use real accounts or production data for failed-login threshold tests.
6. To repeat the manager-role check, use only a disposable local database: temporarily assign `teacher1` the `manager` role in CS101, verify the login, course, and grading checks, then restore `editingteacher`. Do not leave the sample account's role changed.

Raw results (without passwords or tokens) are in [`security/results.json`](security/results.json). The optional OWASP ZAP baseline scan was not run; this report therefore makes no claim about ZAP findings.
