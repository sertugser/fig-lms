# Maintainability KPIs: Moodle baseline

Baseline for the Moodle code of our 6 modules (Moodle 5.3.1, tag `v5.3.1`, commit `cfe123f2`, the same version that `infra/` runs).
At the end of the project the new system is measured the same way and compared against these numbers.

## Module to Moodle folder mapping
Paths are relative to Moodle's `public/` folder. This is the same folder list as the team's cloc line-count table,
plus `user/` for identity. It lives in `MODULES` in [measure.py](maintainability/measure.py), so changing it and re-running is enough.

| Module | Moodle folders and files |
|---|---|
| Identity & access | `auth` (all auth plugins), `login`, `admin/roles`, `user`, `lib/accesslib.php`, `lib/authlib.php`, `lib/classes/access`, `lib/classes/context`, `lib/classes/context.php`, `lib/classes/context_helper.php` (+ their tests in `lib/tests`) |
| Courses & enrolment | `course` (incl. course formats), `enrol` (all enrolment plugins), `lib/enrollib.php`, `lib/modinfolib.php` (+ `lib/tests/modinfolib_test.php`) |
| Advising | `availability`, `completion`, `lib/completionlib.php` (+ test). Closest to prerequisite checks; Moodle has no advising module |
| Assignments | `mod/assign` (incl. submission and feedback sub-plugins) |
| Grading | `grade`, `lib/grade`, `lib/gradelib.php` (+ test) |
| Study planner (new) | `admin/tool/lp`, `competency` (learning plans and competencies; **reference only**, the module is new) |

Not counted anywhere: language packs (`lang/`), generated JS (`amd/build`, `yui/build`), `vendor/`, `node_modules/`,
and third-party libraries listed in Moodle's `thirdpartylibs.xml` files (e.g. `mod/assign/feedback/editpdf/fpdi`, `enrol/lti/ims-blti`).
Shared core code (`lib/moodlelib.php`, `lib/dml`, forms, output) is not assigned to any module.

## Results

### Lines of code (cloc, production code)

| Module | PHP | JavaScript | Mustache | Other (CSS, XML, JSON, ...) | All languages | Files |
|---|---:|---:|---:|---:|---:|---:|
| Identity & access | 30,403 | 1,331 | 607 | 776 | 33,117 | 364 |
| Courses & enrolment | 60,877 | 10,607 | 2,921 | 2,295 | 76,700 | 771 |
| Advising | 7,591 | 1,215 | 10 | 295 | 9,111 | 94 |
| Assignments | 31,693 | 4,841 | 647 | 2,410 | 39,591 | 371 |
| Grading | 34,929 | 3,356 | 1,217 | 1,589 | 41,091 | 445 |
| Study planner (ref.) | 16,494 | 4,733 | 1,871 | 271 | 23,369 | 206 |
| **Total (first 5)** | **165,493** | | | | **199,610** | |

### Tests and complexity (PHP)

| Module | Test lines (PHP) | Test/code | Functions | Mean CCN | Median CCN | Max CCN | Functions with CCN > 10 |
|---|---:|---:|---:|---:|---:|---:|---:|
| Identity & access | 19,251 | 0.63 | 1,484 | 3.31 | 2 | 43 | 86 (5.8%) |
| Courses & enrolment | 50,389 | 0.83 | 2,889 | 3.59 | 2 | 67 | 174 (6.0%) |
| Advising | 7,366 | 0.97 | 483 | 3.61 | 2 | 31 | 36 (7.5%) |
| Assignments | 18,010 | 0.57 | 1,534 | 3.86 | 1 | 91 | 113 (7.4%) |
| Grading | 15,308 | 0.44 | 1,285 | 4.78 | 2 | 107 | 131 (10.2%) |
| Study planner (ref.) | 12,272 | 0.74 | 998 | 2.36 | 1 | 36 | 23 (2.3%) |

Test/code = PHP test lines / PHP production lines. Behat acceptance test lines (`.feature`) are in `results/modules.csv` (`behat_feature_lines`) and are not part of the ratio.

### Test coverage (PHPUnit with PCOV)

| Module | Production PHP files | Executable lines | Covered lines | **Line coverage** | Tests run | Failed | Skipped | Run time (min) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Identity & access | 316 | 20,800 | 5,776 | **27.8%** | 952 | 0 | 11 | 5.4 |
| Courses & enrolment | 546 | 41,823 | 14,437 | **34.5%** | 2,309 | 0 | 12 | 16.8 |
| Advising | 67 | 4,657 | 2,809 | **60.3%** | 314 | 0 | 0 | 1.9 |
| Assignments | 230 | 22,305 | 5,420 | **24.3%** | 646 | 0 | 0 | 5.5 |
| Grading | 303 | 24,616 | 6,780 | **27.5%** | 417 | 1 | 1 | 3.0 |
| Study planner (ref.) | 120 | 11,603 | 5,910 | **50.9%** | 306 | 0 | 1 | 1.6 |
| **Total (first 5)** | 1,462 | 114,201 | 35,222 | **30.8%** | 4,638 | 1 | 24 | 32.6 |

Measured on 2026-11-22 with Moodle `v5.3.1` (`cfe123f2`), PHP 8.3, PHPUnit 11.5.56 and PCOV 1.0.12 in Docker 29.1.2 (7.6 GB memory for Docker),
on a Windows 11 laptop (Intel Core i5-13420H, 16 GB). Numbers are in `results/phpunit/coverage.csv`; the raw reports (clover XML, gzipped as `*-clover.xml.gz`; JUnit XML, PHPUnit output, run time) are in `results/phpunit/`.
How to repeat: [phpunit-coverage/README.md](maintainability/phpunit-coverage/README.md).

- Coverage counts the executable lines of the module's production PHP files (same file set as the line counts above) that the module's own tests run. Files that no test loads count as not covered.
- The one failed test is `reportlib_test::test_blank_hidden_total_and_adjust_bounds` (Grading, `grade/tests/reportlib_test.php:133`). The test hides one of two graded activities and expects the course total to be blanked when the report setting is "hide totals if they contain hidden items"; Moodle returned the full total (120) instead of an empty grade. This is the same symptom as check G14 in [grading.md](../srs/modules/grading.md) (the total still includes the hidden item). Whether it is a Moodle 5.3.1 bug or caused by the test environment is not known yet. The test is counted in the numbers.
- Skipped tests are the ones Moodle skips itself (for example tests that need an external service).
- Only the module's own tests are run. Tests of other parts of Moodle also run this code (for example the assignment tests use the grade code), so these numbers are a **lower bound** of what the whole Moodle test suite covers.
- Behat (browser) tests and JavaScript tests are not included.

### 5 most complex functions per module (CCN)
| Module | Top 5 |
|---|---|
| Identity & access | `get_with_capability_join` 43, `auth_plugin_ldap::sync_users_update_callback` 38, `user_filtering::get_field` 36, `auth_plugin_ldap::user_update` 35, `has_capability` 35 |
| Courses & enrolment | `core_course_external::get_course_contents` 67, `enrol_meta_sync` 65, `enrol_database_plugin::sync_enrolments` 60, `course_edit_form::definition` 56, `enrol_ldap_plugin::sync_enrolments` 54 |
| Advising | `completion_info::update_state` 31, `tree::__construct` 25, `aggregate_completions` 25, `form_trait::definition_after_data_completion` 23, `tree::get_full_information_recursive` 19 |
| Assignments | `assign_grading_table::__construct` 91, `assign::add_grade_form_elements` 87, `assign_grading_table::col_status` 65, `assign::process_save_quick_grades` 65, `assignfeedback_offline_import_grades_form::definition` 62 |
| Grading | `grade_category::aggregate_values_and_adjust_bounds` 107, `user::fill_table_recursive` 99, `grade_report_grader::get_right_rows` 76, `edit_category_form::definition_after_data` 57, `grade_structure::get_cell_action_menu` 56 |
| Study planner (ref.) | `api::add_evidence` 36, `api::filter_users_with_capability_on_user_context_sql` 23, `core_competency_comment_add` 22, `user_competency_summary_exporter::get_other_values` 17, `competency_rule_points::validate_config` 17 |

File and line for each function: `maintainability/results/top5.csv`.

## KPIs

| KPI | Definition | Unit | Moodle baseline | Proposed target (new system) |
|---|---|---|---|---|
| MAINT-01 Mean cyclomatic complexity | Average cyclomatic complexity (CCN) over all functions/methods of the module's production code | CCN per function | 3.3 to 4.8 per module (planner ref. 2.4) | <= 3.0 per module |
| MAINT-02 Max complexity | Highest CCN of any single method | CCN | 31 to 107 per module | <= 15 for every method (enforced in CI) |
| MAINT-03 Complex-function share | Share of functions with CCN > 10 | % | 5.8 to 10.2% (planner ref. 2.3%) | <= 2% |
| MAINT-04 Test size ratio | Test code lines / production code lines | ratio | 0.44 to 0.97 | >= 0.8 per module |
| MAINT-05 Test coverage | Line coverage of the module's production PHP code by the PHPUnit tests in the module's own folders | % | 24.3 to 60.3% per module (5 modules together 30.8%; planner ref. 50.9%) | >= 80% line coverage per module (decided, see below) |

Targets are proposals for discussion; they must also be realistic for Java/Spring (smaller methods are normal there).

**MAINT-05 target (decided 2026-11-23 by the team lead):** keep **>= 80% line coverage** for the new system. It is far above Moodle's 24 to 60% (30.8% over the five modules), but the new code is small and written together with its tests. The Moodle numbers are a baseline to compare with, not a target. If the team finds 80% too strict for code such as controllers, a fallback is "at least 80% for each `api` and `internal` service class, at least Moodle's baseline for the module as a whole". The 80% target was accepted; the fallback stays documented in case it proves too strict for controllers.

## How it was measured (repeatable)
1. `git clone --depth 1 -b v5.3.1 https://github.com/moodle/moodle.git`
2. `pip install lizard` and install cloc (`apt install cloc` or `brew install cloc`). Tested with lizard 1.26.1, cloc 1.98, Python 3.13 (production PHP lines are identical to the team's cloc 2.06 table; that table was made on `MOODLE_503_STABLE` `1156a935`, which differs from `v5.3.1` by 4 non-PHP lines in courses and a few test lines).
3. `python docs/kpi/maintainability/measure.py <moodle>/public docs/kpi/maintainability/results` (about 10 minutes on a laptop)

4. Coverage (MAINT-05, about 35 minutes of test time plus a few minutes each for the image and the PHPUnit set-up): follow [phpunit-coverage/README.md](maintainability/phpunit-coverage/README.md). It builds a test variant of the Moodle image (`infra/moodle/Dockerfile.phpunit`), initialises PHPUnit with its own `phpu_` tables and data folder, runs `run_coverage.py` for every module and `summarize_coverage.py` for the numbers.

Definitions used by `measure.py`:
- **Code lines**: cloc code lines (no blank lines, no comments), per language. The PHP column is the one to compare with the new system's Java code; "All languages" is the full size of what has to be rewritten.
- **Test code**: files with `/tests/` in their path (PHPUnit tests and fixtures). Everything else is production code.
- **Complexity**: lizard cyclomatic complexity per function, production PHP files only.
- A file is counted in one module only; the script stops if two modules share a file.

## Limitations
- Coverage is a lower bound (only the module's own tests, no Behat or JavaScript tests, see the notes under the coverage table). It also treats the large auth and enrolment plugin sets as part of the modules, like the other KPIs.
- Identity and courses include every auth and enrolment plugin (LDAP, database, meta, ...), not only the methods we rebuild. This keeps the numbers equal to the team's line-count table; narrowing the scope is a change in `MODULES`.
- lizard is not a PHP parser; its CCN can differ by a few points from phpmetrics for functions with closures or `match`.
- The `admin/tool/lp` + `competency` numbers are only a reference for the new planner.
