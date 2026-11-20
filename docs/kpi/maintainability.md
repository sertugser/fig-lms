# Maintainability KPIs: Moodle baseline

Baseline for the Moodle code of our 6 modules (Moodle 5.3.1, `MOODLE_503_STABLE`, commit `1156a935`).
At the end of the project the new system is measured the same way and compared against these numbers.

## Module to Moodle folder mapping
Paths are relative to Moodle's `public/` folder. This mapping is **my proposal** (the official folder list was not available);
it lives in `MODULES` in [measure.py](maintainability/measure.py), so changing it and re-running is enough.

| Module | Moodle folders |
|---|---|
| Identity & access | `login`, `auth/manual`, `admin/roles`, `user`, `lib/accesslib.php` |
| Courses & enrolment | `course`, `enrol/manual`, `enrol/self`, `enrol/category` |
| Advising | `availability`, `completion` (closest to prerequisite checks; Moodle has no advising module) |
| Assignments | `mod/assign` |
| Grading | `grade`, `lib/gradelib.php` |
| Study planner (new) | `competency` (learning plans; **reference only**, the module is new) |

## Results

| Module | Code lines | Test lines | Test/code | Functions | Mean CCN | Median CCN | Max CCN | Functions with CCN > 10 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Identity & access | 16,712 | 8,054 | 0.48 | 930 | 3.51 | 2 | 43 | 60 (6.5%) |
| Courses & enrolment | 31,565 | 27,174 | 0.86 | 1,946 | 3.55 | 2 | 67 | 117 (6.0%) |
| Advising | 4,944 | 4,882 | 0.99 | 439 | 3.39 | 2 | 25 | 28 (6.4%) |
| Assignments | 27,526 | 15,992 | 0.58 | 1,742 | 3.84 | 1 | 91 | 125 (7.2%) |
| Grading | 24,169 | 10,477 | 0.43 | 1,055 | 4.73 | 2 | 99 | 107 (10.1%) |
| Study planner (ref.) | 8,776 | 10,804 | 1.23 | 798 | 2.39 | 1 | 36 | 17 (2.1%) |

Behat acceptance test lines (`.feature`) are in `results/modules.csv` (`behat_feature_lines`) and are not part of the test/code ratio.

### 5 most complex functions per module (CCN)
| Module | Top 5 |
|---|---|
| Identity & access | `get_with_capability_join` 43, `user_filtering::get_field` 36, `has_capability` 35, `update_capabilities` 28, `core_role_preset::parse_preset` 27 |
| Courses & enrolment | `core_course_external::get_course_contents` 67, `course_edit_form::definition` 56, `edit_module_post_actions` 48, `moodleform_mod::standard_coursemodule_elements` 41, `core_course_category::search_courses` 38 |
| Advising | `tree::__construct` 25, `form_trait::definition_after_data_completion` 23, `tree::get_full_information_recursive` 19, `condition::load_course_structure` 19, `manager::apply_default_completion` 18 |
| Assignments | `assign_grading_table::__construct` 91, `assign::add_grade_form_elements` 87, `assign_grading_table::col_status` 65, `assign::process_save_quick_grades` 65, `assignfeedback_offline_import_grades_form::definition` 62 |
| Grading | `user::fill_table_recursive` 99, `grade_report_grader::get_right_rows` 76, `edit_category_form::definition_after_data` 57, `grade_structure::get_cell_action_menu` 56, `add_category::definition_after_data` 52 |
| Study planner (ref.) | `api::add_evidence` 36, `api::filter_users_with_capability_on_user_context_sql` 23, `core_competency_comment_add` 22, `competency_rule_points::validate_config` 17, `plan_exporter::get_other_values` 15 |

File and line for each function: `maintainability/results/top5.csv`.

## KPIs

| KPI | Definition | Unit | Moodle baseline | Proposed target (new system) |
|---|---|---|---|---|
| MAINT-01 Mean cyclomatic complexity | Average cyclomatic complexity (CCN) over all functions/methods of the module's production code | CCN per function | 3.4 to 4.7 per module (see table) | <= 3.0 per module |
| MAINT-02 Max complexity | Highest CCN of any single method | CCN | 25 to 99 per module | <= 15 for every method (enforced in CI) |
| MAINT-03 Complex-function share | Share of functions with CCN > 10 | % | 6.0 to 10.1% (planner ref. 2.1%) | <= 2% |
| MAINT-04 Test size ratio | Test code lines / production code lines | ratio | 0.43 to 0.99 | >= 0.8 per module |
| MAINT-05 Test coverage | Line coverage by the unit tests of the module | % | **not measured** (see below) | >= 80% line coverage per module |

Targets are proposals for discussion; they must also be realistic for Java/Spring (smaller methods are normal there).

## How it was measured (repeatable)
1. `git clone --depth 1 -b MOODLE_503_STABLE https://github.com/moodle/moodle.git`
2. `pip install lizard pygount` (tested with lizard 1.26.1, pygount 3.1.0, Python 3.9)
3. `python docs/kpi/maintainability/measure.py <moodle>/public docs/kpi/maintainability/results` (about 10 minutes on a laptop)

Definitions used by the script:
- **Code lines**: pygount code lines of `.php` files (no blank lines, no comments). Language packs (`lang/`), `vendor/`, `node_modules/`, and JS (`amd/`, `yui/`) are excluded.
- **Test code**: PHP files with `/tests/` in their path (PHPUnit tests and fixtures). Everything else is production code.
- **Complexity**: lizard cyclomatic complexity per function, production PHP files only.
- Folders listed for one module are not listed for another, so there is no double counting.

## Limitations
- **Coverage (MAINT-05) is not measured.** It needs PHP with Xdebug/PCOV and a running Moodle with a database for PHPUnit, which was not available (no PHP, Docker not running on this machine). Next step: run PHPUnit with coverage inside the Moodle container once `infra/` exists, then fill the baseline. Until then, treat the 80% target as provisional.
- The line counts come from pygount and may differ slightly from the existing cloc table, which I did not have. They should be reconciled with it if the numbers are published together.
- lizard is not a PHP parser; its CCN can differ by a few points from phpmetrics for functions with closures or `match`.
- The folder mapping is my assumption (see above); the `competency` numbers are only a reference for the new planner.
