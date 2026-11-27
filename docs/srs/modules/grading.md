# Grading rules

Moodle baseline: 5.3.1 (`v5.3.1`, commit `cfe123f2`). Moodle source paths are relative to its `public/` directory; links point to the `v5.3.1` tag.
Scope: grading, weighted mean, and students viewing their own grades (module `grading`).

## Requirements

| ID | Type | Requirement and acceptance criteria | Moodle source |
|---|---|---|---|
| REQ-GRD-01 | Preserved | A grade category combines the grades of its items with one configured aggregation method. The new system supports at least *Mean of grades* and *Weighted mean of grades*; Moodle also has median, min, max, mode, simple weighted mean, mean with extra credits and natural (sum). | constants in [`lib/grade/constants.php`](https://github.com/moodle/moodle/blob/v5.3.1/public/lib/grade/constants.php#L33), `aggregate_values_and_adjust_bounds()` in [`lib/grade/grade_category.php`](https://github.com/moodle/moodle/blob/v5.3.1/public/lib/grade/grade_category.php#L1028) |
| REQ-GRD-02 | Preserved | **Weighted mean.** Every item has a weight (`aggregationcoef`). Each grade is first scaled to 0..1 using the item's minimum and maximum, then the category total is `sum(weight × grade) / sum(weight)`. Items with weight 0 or less are ignored. If the weights add up to 0, the total is empty. Example: A=80 (weight 1), B=40 (weight 3), both out of 100 → total 50. | `GRADE_AGGREGATE_WEIGHTED_MEAN` in [`lib/grade/grade_category.php`](https://github.com/moodle/moodle/blob/v5.3.1/public/lib/grade/grade_category.php#L1126), scaling in `aggregate_grades()` ([`#L605`](https://github.com/moodle/moodle/blob/v5.3.1/public/lib/grade/grade_category.php#L605)) |
| REQ-GRD-03 | Preserved | **Empty grades.** With the category setting *Exclude empty grades* (`aggregateonlygraded`, Moodle default: on), an item without a grade is left out of the calculation. With it off, an empty item counts as 0 and lowers the total. If every item is empty, the total stays empty in both cases. Example (A=80, B=40, C empty): mean excluding empty = 60, including empty = 40. | `aggregateonlygraded` in `aggregate_grades()` ([`#L694`](https://github.com/moodle/moodle/blob/v5.3.1/public/lib/grade/grade_category.php#L694), [`#L737`](https://github.com/moodle/moodle/blob/v5.3.1/public/lib/grade/grade_category.php#L737)), default in [`admin/settings/grades.php`](https://github.com/moodle/moodle/blob/v5.3.1/public/admin/settings/grades.php#L133) |
| REQ-GRD-04 | Preserved | **Drop lowest / keep highest.** A category can drop its *n* lowest or keep its *n* highest grades (after scaling) before aggregating. The two options exclude each other: when both are set, *drop lowest* wins and *keep highest* is reset to 0. Example: grades 80 and 40 (C empty, excluded), drop lowest 1 → total 80. Moodle does not apply the rule in every setup (for natural aggregation it needs equal weights and maximums, no sub-categories and no extra credit). | `apply_limit_rules()` ([`#L1781`](https://github.com/moodle/moodle/blob/v5.3.1/public/lib/grade/grade_category.php#L1781)), `can_apply_limit_rules()` ([`#L1881`](https://github.com/moodle/moodle/blob/v5.3.1/public/lib/grade/grade_category.php#L1881)), exclusivity in `grade_category::update()` ([`#L256`](https://github.com/moodle/moodle/blob/v5.3.1/public/lib/grade/grade_category.php#L256)) |
| REQ-GRD-05 | Preserved | **Grade limits.** A saved grade is limited to the item's minimum and maximum. A grade of 150 for an item out of 100 is stored as 100, and -5 for a minimum of 0 is stored as 0 (Moodle's "unlimited grades" option, off by default, lifts the maximum). | `bounded_grade()` in [`lib/grade/grade_item.php`](https://github.com/moodle/moodle/blob/v5.3.1/public/lib/grade/grade_item.php#L1692), `update_final_grade()` ([`#L1869`](https://github.com/moodle/moodle/blob/v5.3.1/public/lib/grade/grade_item.php#L1869)) |
| REQ-GRD-06 | Preserved | **Students see only their own grades.** A student can read the grades of their own user in a course only if all hold: capability `gradereport/user:view`, capability `moodle/grade:view`, and the course setting *Show gradebook to students* is on. Asking for another user's grades, or for all grades of the course, is refused. | `check_report_access()` in [`grade/report/user/classes/external/user.php`](https://github.com/moodle/moodle/blob/v5.3.1/public/grade/report/user/classes/external/user.php#L56) (rules at [`#L80`](https://github.com/moodle/moodle/blob/v5.3.1/public/grade/report/user/classes/external/user.php#L80)-[`#L108`](https://github.com/moodle/moodle/blob/v5.3.1/public/grade/report/user/classes/external/user.php#L108)), capabilities in [`lib/db/access.php`](https://github.com/moodle/moodle/blob/v5.3.1/public/lib/db/access.php#L1670) and [`grade/report/user/db/access.php`](https://github.com/moodle/moodle/blob/v5.3.1/public/grade/report/user/db/access.php#L28) |
| REQ-GRD-07 | Preserved | **Hidden grades.** An item or grade marked hidden is not shown to students (the user report setting *Show hidden items* defaults to "only until the date"). A teacher with `moodle/grade:viewhidden` still sees it. Whether a category or course total is shown, or recalculated without the hidden item, is a separate report setting (`grade_report_user_showtotalsifcontainhidden`, default: hide the total in the report). | `is_hidden()` in [`lib/grade/grade_grade.php`](https://github.com/moodle/moodle/blob/v5.3.1/public/lib/grade/grade_grade.php#L626), `get_hiding_affected()` ([`#L806`](https://github.com/moodle/moodle/blob/v5.3.1/public/lib/grade/grade_grade.php#L806)), report settings in [`grade/report/user/settings.php`](https://github.com/moodle/moodle/blob/v5.3.1/public/grade/report/user/settings.php#L118) |
| REQ-GRD-08 | Preserved | **Only staff change and see everything.** Editing grades needs `moodle/grade:edit`, and viewing all students' grades needs `moodle/grade:viewall`. Both are given to the roles `teacher`, `editingteacher` (project role teacher) and `manager` (project role admin), not to `student`. | `moodle/grade:viewall`, `moodle/grade:edit` in [`lib/db/access.php`](https://github.com/moodle/moodle/blob/v5.3.1/public/lib/db/access.php#L1658) |

## Golden master scenarios

Each scenario was run against the Docker Moodle 5.3.1 with the sample users (`student1`, `student2`) in a temporary course; the check IDs refer to [`verify_results.txt`](grading/verify_results.txt). The same inputs go to the new system, and its answers must match the Moodle values unless a difference is recorded in this file.

| Kind | Scenario (input) | Moodle result | Check |
|---|---|---|---|
| Normal | Weighted mean, A=80 (w 1), B=40 (w 3), C empty (w 2), empty excluded | 50 | G1 |
| Normal | Same items, empty counted as 0 | 33.33 | G2 |
| Normal | Mean of grades, empty excluded / counted as 0 | 60 / 40 | G3, G4 |
| Normal | Mean, drop lowest 1 (empty excluded / counted as 0) | 80 / 60 | G6, G7 |
| Boundary | Student without any grade | course total empty | G5 |
| Boundary | Drop lowest 1 together with keep highest 2 | keep highest reset to 0 | G8 |
| Invalid input | Grade 150 for a maximum of 100 | stored as 100 | G9 |
| Invalid input | Grade -5 for a minimum of 0 | stored as 0 | G10 |
| Visibility | Student reads own grades | items A, B, C and course total visible | G11, G12 |
| Visibility | Item B hidden | student does not see B; web service total still 50 | G13, G14 |
| Unauthorized | `student1` asks for `student2`'s grades | error `nopermissiontoviewgrades` | G15 |
| Unauthorized | `student1` asks for all grades of the course (user 0) | error `nopermissions` | G16 |
| Unauthorized | *Show gradebook to students* off, student asks own grades | error `nopermissiontoviewgrades` | G17 |
| Unauthorized | Student's capabilities `moodle/grade:edit` / `moodle/grade:viewall` | denied / denied | G18 |

Not covered yet: sub-categories, extra credit, scales, and the HTML user report (only the web service was called). The golden master REST token belongs to `admin`, so student-side scenarios (G11-G17) need a token or a login as the student; until then they run in Moodle through `verify_rules.php`.

## Data model

Table and column names are from Moodle 5.3.1 [`lib/db/install.xml`](https://github.com/moodle/moodle/blob/v5.3.1/public/lib/db/install.xml#L1997).

| Table | Key columns | Purpose |
|---|---|---|
| `grade_categories` | `id`, `courseid`, `parent`, `depth`, `path`, `fullname`, `aggregation`, `keephigh`, `droplow`, `aggregateonlygraded`, `aggregateoutcomes`, `hidden` | A group of grade items with one aggregation method; categories form a tree per course. The course itself has a root category. |
| `grade_items` | `id`, `courseid`, `categoryid`, `itemname`, `itemtype` (`course`, `category`, `mod`, `manual`), `itemmodule`, `iteminstance`, `itemnumber`, `gradetype`, `grademax`, `grademin`, `gradepass`, `aggregationcoef` (weight / extra credit flag), `aggregationcoef2`, `weightoverride`, `hidden`, `locked`, `needsupdate` | A gradable column: an activity grade, a manual item, or the total of a category or course. `needsupdate` marks totals that must be recalculated. |
| `grade_grades` | `id`, `itemid`, `userid`, `rawgrade`, `rawgrademax`, `rawgrademin`, `finalgrade`, `hidden`, `locked`, `overridden`, `excluded`, `feedback`, `aggregationstatus`, `aggregationweight` | One user's grade for one item: the raw grade from the activity, the final grade after limits and aggregation, and flags that hide, lock or override it. |

Relations: `grade_items.categoryid → grade_categories.id`, `grade_grades.itemid → grade_items.id`, `grade_grades.userid → user.id`. The total of a category is a `grade_items` row of type `category` that belongs to that category.

## Decisions in the new system

- **Hidden items and totals (REQ-GRD-07).** The new system follows the HTML user report default, not the web service: when a student's course total contains a hidden item or a hidden grade, the student's report leaves the total out (`totalHidden: true`). Teachers with `moodle/grade:viewhidden` see the total. This settles the G14 difference noted below.
- **Aggregation methods (REQ-GRD-01).** Mean, weighted mean, median, minimum and maximum are implemented. The site default is *Mean of grades* (`fig.grading.default-aggregation`). *Natural* (sum of grades, Moodle's default since 2.8) is not implemented yet, because its weight adjustment rules are not part of REQ-GRD-01..08; mode, simple weighted mean and mean with extra credits are not implemented either.
- **Totals are computed when read.** Category and course totals are calculated from the stored grades on every report (`GradeCalculator`), so there is no `needsupdate` flag and no stored total that can go stale.
- **Activity grades are changed in the activity.** The gradebook refuses to edit a grade of an activity item (`GRADE_IN_ACTIVITY`); activities push their grades through `GradebookService.pushGrade()` as Moodle activities call `grade_update()`.

## Verification notes

- [`grading/verify_rules.php`](grading/verify_rules.php) creates a temporary course in the Docker Moodle, builds the items above, calls Moodle's own grade API and the `gradereport_user_get_grade_items` web service function as `student1`, and deletes the course. Output: [`grading/verify_results.txt`](grading/verify_results.txt) (18 checks, all passed on 2026-11-22).
- Run it with: `docker compose -f infra/docker-compose.yml exec -T -u www-data moodle php < docs/srs/modules/grading/verify_rules.php` (PowerShell: pipe the file with `Get-Content`).
- The course total shown by the web service in G14 includes the hidden item, while the default of the HTML report setting hides the total. This difference between the two outputs is observed, not yet explained; the new system should state which of the two it follows.
- MDL-85503 is listed in the task of this module, but the ticket is about course categories, not grading. See [`docs/bugs/MDL-85503.md`](../../bugs/MDL-85503.md).
