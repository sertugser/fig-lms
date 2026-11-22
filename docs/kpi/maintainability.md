# Maintainability KPIs: Moodle baseline

Baseline for the Moodle code of our 6 modules (Moodle 5.3.1, `MOODLE_503_STABLE`, commit `1156a935`).
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
| Courses & enrolment | 60,877 | 10,607 | 2,921 | 2,299 | 76,704 | 771 |
| Advising | 7,591 | 1,215 | 10 | 295 | 9,111 | 94 |
| Assignments | 31,693 | 4,841 | 647 | 2,410 | 39,591 | 371 |
| Grading | 34,929 | 3,356 | 1,217 | 1,589 | 41,091 | 445 |
| Study planner (ref.) | 16,494 | 4,733 | 1,871 | 271 | 23,369 | 206 |
| **Total (first 5)** | **165,493** | | | | **199,614** | |

### Tests and complexity (PHP)

| Module | Test lines (PHP) | Test/code | Functions | Mean CCN | Median CCN | Max CCN | Functions with CCN > 10 |
|---|---:|---:|---:|---:|---:|---:|---:|
| Identity & access | 19,251 | 0.63 | 1,484 | 3.31 | 2 | 43 | 86 (5.8%) |
| Courses & enrolment | 50,408 | 0.83 | 2,889 | 3.59 | 2 | 67 | 174 (6.0%) |
| Advising | 7,371 | 0.97 | 483 | 3.61 | 2 | 31 | 36 (7.5%) |
| Assignments | 18,010 | 0.57 | 1,534 | 3.86 | 1 | 91 | 113 (7.4%) |
| Grading | 15,304 | 0.44 | 1,285 | 4.78 | 2 | 107 | 131 (10.2%) |
| Study planner (ref.) | 12,272 | 0.74 | 998 | 2.36 | 1 | 36 | 23 (2.3%) |

Test/code = PHP test lines / PHP production lines. Behat acceptance test lines (`.feature`) are in `results/modules.csv` (`behat_feature_lines`) and are not part of the ratio.

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
| MAINT-05 Test coverage | Line coverage by the unit tests of the module | % | **not measured** (see below) | >= 80% line coverage per module |

Targets are proposals for discussion; they must also be realistic for Java/Spring (smaller methods are normal there).

## How it was measured (repeatable)
1. `git clone --depth 1 -b MOODLE_503_STABLE https://github.com/moodle/moodle.git`
2. `pip install lizard` and install cloc (`apt install cloc` or `brew install cloc`). Tested with lizard 1.26.1, cloc 1.98, Python 3.13 (line counts are identical to the team's cloc 2.06 table).
3. `python docs/kpi/maintainability/measure.py <moodle>/public docs/kpi/maintainability/results` (about 10 minutes on a laptop)

Definitions used by the script:
- **Code lines**: cloc code lines (no blank lines, no comments), per language. The PHP column is the one to compare with the new system's Java code; "All languages" is the full size of what has to be rewritten.
- **Test code**: files with `/tests/` in their path (PHPUnit tests and fixtures). Everything else is production code.
- **Complexity**: lizard cyclomatic complexity per function, production PHP files only.
- A file is counted in one module only; the script stops if two modules share a file.

## Limitations
- **Coverage (MAINT-05) is not measured.** It needs PHP with Xdebug/PCOV and a running Moodle with a database for PHPUnit. Next step: run PHPUnit with coverage inside the Moodle container once `infra/` exists, then fill the baseline. Until then, treat the 80% target as provisional.
- Identity and courses include every auth and enrolment plugin (LDAP, database, meta, ...), not only the methods we rebuild. This keeps the numbers equal to the team's line-count table; narrowing the scope is a change in `MODULES`.
- lizard is not a PHP parser; its CCN can differ by a few points from phpmetrics for functions with closures or `match`.
- The `admin/tool/lp` + `competency` numbers are only a reference for the new planner.
