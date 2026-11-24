# KPI table

All KPIs of the project in one table, with the Moodle 5.3.1 baseline and the target for the new system. The numbers are copied from the category files under [`docs/kpi/`](../kpi/), which also hold the raw data and the steps to repeat each measurement. If a number changes there, change it here in the same PR.

Baselines were measured on 2026-11-22/23 on Moodle `v5.3.1` (`cfe123f2`) with the sample data from `infra/sample-data`. Usability lists the five flows in the order FLOW-01 submit an assignment, FLOW-02 view own grade, FLOW-03 create an assignment, FLOW-04 grade a submission, FLOW-05 self-enrol.

| KPI | Category | Unit | Moodle baseline | Target (new system) | How measured | Owner |
|---|---|---|---|---|---|---|
| MAINT-01 Mean cyclomatic complexity | Maintainability | CCN per function | 3.3 to 4.8 per module (planner ref. 2.4) | ≤ 3.0 per module | lizard over the module's production code, [`measure.py`](../kpi/maintainability/measure.py) | @ogithup |
| MAINT-02 Max complexity | Maintainability | CCN | 31 to 107 per module | ≤ 15 for every method (CI rule) | lizard, highest CCN of any method | @ogithup |
| MAINT-03 Complex-function share | Maintainability | % of functions with CCN > 10 | 5.8 to 10.2 % (planner ref. 2.3 %) | ≤ 2 % | lizard | @ogithup |
| MAINT-04 Test size ratio | Maintainability | test lines / production lines | 0.44 to 0.97 | ≥ 0.8 per module | cloc, files under `/tests/` vs the rest | @ogithup |
| MAINT-05 Test coverage | Maintainability | % line coverage | 24.3 to 60.3 % per module (5 modules: 30.8 %) | ≥ 80 % per module (decided 2026-11-23) | PHPUnit + PCOV on Moodle; [phpunit-coverage](../kpi/maintainability/phpunit-coverage/README.md) | @ogithup |
| SEC-01 Cross-course URL access | Security (A01) | allow / deny | Non-enrolled student redirected to the enrolment page; no content served | Deny 100 % of unauthorised course requests | Direct URL as `student5` | @HasanBasriEngin |
| SEC-02 Enrolled course access | Security (A01) | allow / deny | Enrolled student allowed (HTTP 200) | Allow enrolled, deny others, 100 % | Direct URL as `student1` | @HasanBasriEngin |
| SEC-03 Student grading access | Security (A01) | allow / deny | Denied (HTTP 404, no `mod/assign:grade`) | Deny 100 %, also for direct requests | Grading URL as `student1` | @HasanBasriEngin |
| SEC-04 Teacher/manager grading access | Security (A01) | allow / deny | Allowed for teacher and course manager (HTTP 200) | Allow only assigned teachers and managers | Grading URL as `teacher1` and a temporary manager | @HasanBasriEngin |
| SEC-05 Assignment creation rights | Security (A01) | capability per role | student no; teacher, manager, admin yes | Match the role matrix in 100 % of checks | [`measure.php`](../kpi/security/measure.php) | @HasanBasriEngin |
| SEC-06 Manual enrolment rights | Security (A01) | capability per role | student no; teacher, manager, admin yes | Match the role matrix in 100 % of checks | [`measure.php`](../kpi/security/measure.php) | @HasanBasriEngin |
| SEC-07 Invalid password | Security (A07) | authenticated sessions | 0 | 0 | One wrong password, then `/my/` | @HasanBasriEngin |
| SEC-08 Failed-login lockout | Security (A07) | attempts, seconds | Disabled (threshold 0) | 5 failures per 15 min, 15 min lockout | `$CFG` values via `measure.php` | @HasanBasriEngin |
| SEC-09 Password policy | Security (A07) | characters, classes | 8 characters + 4 character classes; not checked at login | ≥ 12 characters | `$CFG` values via `measure.php` | @HasanBasriEngin |
| SEC-10 Inactive session timeout | Security (A05) | seconds | 28,800 (8 h) | ≤ 7,200 (2 h) | `$CFG->sessiontimeout` | @HasanBasriEngin |
| SEC-11 SQL error disclosure | Security (A03) | DB errors shown | 0 (search for `' OR '1'='1` returned a normal page) | 0 | Course search as a student | @HasanBasriEngin |
| USAB-01 Click count | Usability | clicks per flow | 6, 2, 8, 7, pending | 6, 2, 6, 5, 3 | Human-operated runs, counting rules in [usability.md](../kpi/usability.md) | @SercanFurkan |
| USAB-02 Step count | Usability | actions per flow | 7, 2, 9, 8, pending | 7, 2, 7, 6, 4 | Human-operated runs | @SercanFurkan |
| USAB-03 Page loads | Usability | documents per flow | 6, 2, 4, 4, pending | 5, 2, 2, 3, 3 | Human-operated runs, browser network log | @SercanFurkan |
| USAB-04 Form fields | Usability | controls per flow | 1, 0, 1, 1, pending | 1, 0, 1, 1, 1 | Human-operated runs | @SercanFurkan |
| PERF-01 Login time | Performance | ms (median / p95) | 90.1 / 115.8 | p95 ≤ 115 ms | k6, [`moodle-perf.js`](../kpi/performance/moodle-perf.js): login POST + redirect to the dashboard; median over 3 runs | @efekanegeli2 |
| PERF-02 Course page | Performance | ms (median / p95) | 37.6 / 40.4 | p95 ≤ 40 ms | k6, [`moodle-perf.js`](../kpi/performance/moodle-perf.js): course page, enrolled student; median over 3 runs | @efekanegeli2 |
| PERF-03 Assignment page | Performance | ms (median / p95) | 49.9 / 59.2 | p95 ≤ 60 ms | k6, [`moodle-perf.js`](../kpi/performance/moodle-perf.js): submission status page, student; median over 3 runs | @efekanegeli2 |
| PERF-04 Gradebook | Performance | ms (median / p95) | 46.9 / 51.1 | p95 ≤ 50 ms | k6, [`moodle-perf.js`](../kpi/performance/moodle-perf.js): grader report, teacher; median over 3 runs | @efekanegeli2 |
| PERF-05 REST call | Performance | ms (median / p95) | 12.0 / 15.6 | p95 ≤ 15 ms | k6, [`moodle-perf.js`](../kpi/performance/moodle-perf.js): `core_course_get_courses`; median over 3 runs | @efekanegeli2 |
| PERF-06 Course page under load | Performance | ms (median / p95) | 80.3 / 158.5, 0 % errors | p95 ≤ 160 ms, 0 % errors | k6, [`moodle-perf.js`](../kpi/performance/moodle-perf.js): 20 concurrent students for 60 s; median over 3 runs | @efekanegeli2 |

Notes:
- **MAINT:** the advising baseline (Moodle `availability`, `completion`, `lib/completionlib.php`) is 9,111 lines, mean CCN 3.61, max 31, coverage 60.3 %. Per-module numbers: [maintainability.md](../kpi/maintainability.md).
- **SEC:** development baselines on the local Docker instance, not a security certification. The optional ZAP scan was not run.
- **USAB:** FLOW-05 is still pending a completed human run; the URL-assisted run gave 4 / 6 / 4 / 1. Usability IDs `USAB-01`…`04` are given here; [usability.md](../kpi/usability.md) names them by their names (click count, …).
- **PERF:** from [performance.md](../kpi/performance.md) (#41, measured 2026-11-22 on an Apple M2 laptop). Server response time only, warm cache, very small dataset; only comparable on the same machine with the same data. Targets: the new system must not be slower than Moodle.
