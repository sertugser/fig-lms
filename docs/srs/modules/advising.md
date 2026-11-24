# Module: Advising (`REQ-ADV`)

| Owner | Issue | Moodle version | Status |
|---|---|---|---|
| @sertugser | #4 | 5.3.1 (`v5.3.1`, Build 20261112) | Draft for review |

**Scope:** Moodle has no advising module. Its closest equivalents are **access restrictions** (activities and sections that open only when conditions are met) and **course completion** (when a course counts as finished). This file documents how both work today and the gap described in MDL-22648: a course cannot require another course before a student enrols. Advisor approval and the credit limit (README scope) are new features without a Moodle equivalent and get their own requirements later.

**How the rules were found:** by reading the Moodle source (paths below are relative to `public/`) and then running each rule against the Docker Moodle from #8 with the sample data from `infra/sample-data`:
- [`advising/verify_rules.php`](advising/verify_rules.php) builds restricted pages in CS101, evaluates them for the sample students with Moodle's own classes and checks 39 scenarios: **39 passed**, see [`advising/verify_results.txt`](advising/verify_results.txt). The script rolls back everything it creates, so it can be run again at any time.

Requirement row format follows the SRS convention from #3: ID, type (preserved / bug fix / new), description, Moodle source, acceptance criteria, owner.

## Terms
| Term | Meaning in Moodle |
|---|---|
| Restriction (availability condition) | A condition on an activity or section, such as "Assignment 1 is complete" or "from 1 Dec". Stored as JSON in `course_modules.availability` / `course_sections.availability`. |
| Restriction set (tree) | Conditions combined with AND or OR, optionally negated, and nested. |
| Activity completion | Per user and activity: incomplete, complete, complete and passed, complete and failed (`course_modules_completion.completionstate` 0–3). |
| Course completion | Per user and course: complete when the course's completion criteria are met (`course_completions.timecompleted` set). |
| Completion of other courses | A course completion criterion: "course Y is complete only if course X is complete". Moodle once called it "Course prerequisites" (renamed in MDL-22259). It controls completion, **not** access or enrolment. |
| Prerequisite (this project) | Course X must be completed **before** a student can enrol in course Y. Moodle has no such rule (MDL-22648). |

## Requirements

| ID | Type | Description | Moodle source | Acceptance criteria | Owner |
|---|---|---|---|---|---|
| REQ-ADV-01 | preserved | **Restriction tree.** An item without restrictions is available. Conditions are combined in a tree with one of four operators: AND (`&`), OR (`\|`), NOT AND (`!&`), NOT OR (`!\|`). AND needs every child, OR needs one child, and the NOT forms invert the children (NOT AND = none of them may hold). Trees can be nested. An unknown operator is rejected. | `availability/classes/tree.php` `__construct()`, `check_available()`, `get_logic_flags()`; `availability/classes/info.php` `is_available()` | 1. No restriction → available. 2. AND(activity complete, from yesterday): student1 yes, student3 no. 3. OR(grade ≥ 80 %, activity incomplete): student1 (85) yes, student2 (72, passed) no, student3 (40, failed) yes. 4. NOT(activity complete): student1 no, student4 yes. 5. Operator `xor` → `coding_exception` "unknown ->op". | @sertugser |
| REQ-ADV-02 | preserved | **Activity completion condition.** Requires another activity to be in a given state: complete, incomplete, complete and passed, or complete and failed. "Complete" also accepts *complete and passed*, but **not** *complete and failed*. "Incomplete" also accepts *complete and failed*. The other two states need an exact match. A condition on a missing or deleted activity is never met, even inside NOT. The special value "previous activity with completion" (`cm = -1`) refers to the closest earlier activity that has completion tracking. | `availability/condition/completion/classes/condition.php` `is_available()`, `get_cmid()`; `lib/completionlib.php` constants `COMPLETION_*` | 1. Requires complete: student1 (passed) yes; student3 (failed) no; student4 (submitted, not graded) no. 2. Requires complete and failed: student3 yes, student1 no. 3. Missing activity → no, and NOT(missing activity) → also no. | @sertugser |
| REQ-ADV-03 | preserved | **Date condition.** "From T" is met when now ≥ T (inclusive). "Until T" is met when now < T (exclusive). NOT swaps the direction. | `availability/condition/date/classes/condition.php` `is_available_for_all()`, `get_logical_direction()` | 1. From T at T → yes; at T − 1 s → no. 2. Until T at T − 1 s → yes; at T → no. | @sertugser |
| REQ-ADV-04 | preserved | **Grade condition.** Compares the user's grade in a grade item as a percentage (0–100) of the item's range. Minimum is inclusive (score ≥ min), maximum is exclusive (score < max). A user without a grade never meets the condition, whatever the limits. | `availability/condition/grade/classes/condition.php` `is_available()`, `get_cached_grade_score()` | 1. ≥ 72 %: student1 (85) yes, student2 (exactly 72) yes. 2. < 72 %: student2 (72) no, student3 (40) yes. 3. ≥ 0 %: student4 (not graded) no. | @sertugser |
| REQ-ADV-05 | preserved | **Course completion.** A course with completion enabled is complete for a user when its criteria are met. Criteria types: self, date, unenrol, activity, duration, grade, role (manual by others), course (completion of other courses). Activity, course and role criteria are first aggregated within their own type (ALL or ANY), then everything is aggregated with the overall method (ALL by default). Completion is stored in `course_completions.timecompleted`; the scheduled task `completion_regular_task` updates it, so it can lag behind the events by up to a cron run. | `lib/completionlib.php` `completion_info::get_aggregation_method()`, `is_course_complete()`, `aggregate_completions()`, `completion_cron_aggregate()`; `completion/criteria/*.php` | 1. CS201 overall method = ALL. 2. CS201 "Completion of other courses: CS101" is met for student1, not for student3. 3. CS201 is not complete for student1: CS101 is done but the teacher has not marked it (ALL). 4. With completion disabled no criteria are evaluated. | @sertugser |
| REQ-ADV-06 | preserved | **What the student sees.** If a restriction is not met, the item is either shown greyed out with the reason ("Not available unless: The activity Assignment 1 is marked complete"; OR trees: "Not available unless any of:"), or, when the restriction is marked hidden (eye icon), not shown at all. A hidden child in an AND tree hides the whole item. | `availability/classes/tree.php` `check_available()` (`$totallyhide`), `get_result_information()`; `lang/en/availability.php` `list_root_and`, `list_root_or`; `course/classes/cm_info.php` `uservisible`, `is_visible_on_course_page()` | 1. Shown restriction, student3: not accessible, still on the course page, text starts with "Not available unless". 2. Hidden restriction, student3: not on the course page and no text. | @sertugser |
| REQ-ADV-07 | preserved | **Who may bypass restrictions.** Users with `moodle/course:ignoreavailabilityrestrictions` (manager, course creator, editing teacher and non-editing teacher by default, `lib/db/access.php`) see and open every item. For everyone else, the restriction is enforced on the server: opening the item by its URL fails in `require_login()`, not only in the UI. | `availability/classes/info_module.php` `get_view_hidden_capability()`; `course/classes/cm_info.php` (`uservisible`); `lib/moodlelib.php` `require_login()` | 1. teacher1 has the capability in CS101 and can open the hidden item. 2. student3 does not have it. 3. student3 opening the restricted page directly → `require_login_exception` "Course or activity not accessible." | @sertugser |
| REQ-ADV-08 | preserved | **Completion of other courses affects only completion.** The course criterion is met when the other course is complete for the user; it does not limit enrolment or access. No enrolment plugin checks it, and no core restriction can refer to another course (installed conditions: completion, date, grade, group, grouping, profile). This is the gap in MDL-22648. | `completion/criteria/completion_criteria_course.php` `review()`, `cron()`; `enrol/self/lib.php` `can_self_enrol()`; `lib/enrollib.php` `enrol_user()` | 1. student3 is enrolled in CS201 although CS101 is not complete. 2. With self enrolment on, student4 (CS101 not complete) may self enrol in CS201. 3. No availability plugin refers to another course. | @sertugser |
| REQ-ADV-09 | new | **Course prerequisite check (MDL-22648).** A course can list prerequisite courses. A student may enrol only if every prerequisite is completed (AND, as Moodle's default aggregation). The check returns allowed / not allowed, a reason and the missing prerequisites. "Completed" has Moodle's meaning: a completion record with a completion time. Students may only check themselves; teachers and admins may check anyone. Enrolment (course module) and the study planner call this check through `advising.api`. A teacher override and advisor approval are later requirements. | New: `advising.api.PrerequisiteService` (#6). Moodle reference for "completed": `course_completions.timecompleted`, `completion/completion_completion.php` `is_complete()` | 1. student1 → CS201 allowed (`PREREQUISITES_COMPLETED`). 2. student3 → CS201 not allowed, missing `CS101`. 3. Course without prerequisites → allowed (`NO_PREREQUISITES`). 4. Unknown student or course → 404; missing parameter → 400. 5. No login → 401; student3 asking about student1 → 403. | @sertugser |

## Golden master scenarios

Each scenario has the same input for Moodle and for the new system, and the result must match. Line references are in [`advising/verify_results.txt`](advising/verify_results.txt).

### Prerequisite rule (REQ-ADV-08 / REQ-ADV-09, used by #13)

Moodle cannot answer "may student X enrol in CS201?". The comparable Moodle fact is the student's completion of the prerequisite: `core_completion_get_course_completion_status` for CS101 (snapshots `completion-cs101-<student>.json`, field `completed`). The new system must give `allowed = completed` for CS201.

| Scenario | Input | Moodle (CS101 `completed`) | New system (`GET /api/advising/prerequisite-check`) |
|---|---|---|---|
| Normal | student1 → CS201 (CS101 passed with 85) | `true` | 200, `allowed: true`, `PREREQUISITES_COMPLETED` |
| Boundary | student3 → CS201 (CS101 attempted, failed with 40 < pass 50); student4 → CS201 (submitted, not graded) | `false` | 200, `allowed: false`, `missingPrerequisites: ["CS101"]` |
| Invalid input | course `CS999`, student `nobody`, missing `course` parameter | `core_completion_get_course_completion_status` returns an error (unknown course or user) | 404 `UNKNOWN_COURSE` / 404 `UNKNOWN_STUDENT` / 400 `INVALID_REQUEST` |
| Unauthorized | student3 asks about student1; no login | `core_completion_get_course_completion_status` for another user fails with `cannotviewreport` (`completion_can_view_data()` in `completion/classes/external.php`) | 403; 401 |

Note the deliberate difference: Moodle lets student3 into CS201 anyway (REQ-ADV-08), the new system says no.

Automated in `tests/golden-master/src/test/java/com/figlms/goldenmaster/advising/PrerequisiteGoldenMasterTest.java` (#13), with student5 (did not submit) as the "not completed" case. The unauthorized row is compared with the Moodle rule above, not a live call, because the harness token belongs to `admin`.

### Preserved restriction rules

| Requirement | Normal | Boundary | Invalid input | Unauthorized access |
|---|---|---|---|---|
| REQ-ADV-01 | AND(complete, from yesterday): student1 yes, student3 no | student3 (failed) meets "incomplete" in an OR tree; NOT(complete): student4 yes | Operator `xor` → `coding_exception` | (see REQ-ADV-07) |
| REQ-ADV-02 | Requires complete: student1 yes; requires failed: student3 yes | Complete and failed does not count as complete; not graded does not count | Missing activity → never met, also inside NOT | (see REQ-ADV-07) |
| REQ-ADV-03 | From T at T − 1 s no; until T at T − 1 s yes | From T at T yes; until T at T no | n/a (direction other than `>=` / `<` → `coding_exception`) | n/a |
| REQ-ADV-04 | ≥ 72 %: student1 yes; < 72 %: student3 yes | Exactly 72 %: ≥ 72 yes, < 72 no | No grade (student4) → never met | n/a |
| REQ-ADV-05 | CS101 criterion of CS201 met for student1 | CS201 not complete with only one of two criteria (ALL) | Completion disabled → nothing evaluated | n/a |
| REQ-ADV-06 | Shown: greyed out with "Not available unless: …" | Hidden: not on the course page at all | n/a | n/a |
| REQ-ADV-07 | teacher1 opens the hidden item | n/a | n/a | student3 has no `moodle/course:ignoreavailabilityrestrictions`; direct URL → `require_login_exception` |

## MDL-22648: why Moodle has no course prerequisites

**The ticket.** [MDL-22648](https://moodle.atlassian.net/browse/MDL-22648) "Restrict course availability based on prerequisite course completion" is a *New Feature* request opened in June 2012 and still unresolved (status category "To Do", no assignee). It has 99 votes and 73 watchers. Components: Course, Course completion, Restrict access. The request: tie a course's availability to the completion of another course, for example "finish the online orientation before your other courses open". At least four later requests were closed as duplicates of it (MDL-37038, MDL-39053, MDL-46104, MDL-49300).

**Why it is still missing.**
1. **The name existed, the feature did not.** The course completion criterion "Completion of other courses" was originally labelled "Course prerequisites". It only decides whether course Y counts as *complete*, so it was renamed to "Completion dependencies" (MDL-22259) to stop the confusion. The code still calls the variable `$prerequisite` (`lib/completionlib.php` `aggregate_completions()`).
2. **Access restrictions stop at the course boundary.** The availability API works on activities and sections inside one course; none of the six core conditions can refer to another course (REQ-ADV-08). Extending restrictions to the course level was a separate request (MDL-64817).
3. **Enrolment is spread over plugins.** Each enrolment method (manual, self, cohort, payment, …) decides on its own whether a user may join, and none of them looks at completion. A community patch (2012–2014) added a prerequisite check to self enrolment only. In review, the required work was listed as: a database table with indexes, backup and restore, a hook so every enrolment plugin can opt in or out, a capability that lets teachers enrol users who have not met the prerequisite, and unit and Behat tests. The patch was not finished and the ticket has had no owner since.

**How our system handles it (REQ-ADV-09, #6).** The advising module owns the rule, so it is checked in one place instead of in every enrolment plugin:
- A course lists its prerequisite courses (`adv_prerequisite`); all of them must be completed (AND).
- `PrerequisiteService.check(student, course)` in `advising.api` answers allowed / not allowed with a reason and the missing courses. The course module calls it before any enrolment, and the study planner uses it to order courses.
- "Completed" means the same as in Moodle (a completion time is set), so the golden master compares the new decision with Moodle's completion status.
- Planned next: teacher or advisor override (the capability asked for in the ticket review), and the rule in the enrolment flow of the course module.

## Data model

From `lib/db/install.xml`. Only the columns these rules use are listed.

| Table | Key columns | Constraints and notes |
|---|---|---|
| `course_modules` / `course_sections` | `id`, `course`, `availability` (JSON text, null = no restriction), `completion` (0 none, 1 manual, 2 automatic) | The restriction tree is stored as JSON per activity or section, not in its own table. |
| `course_modules_completion` | `id`, `coursemoduleid` → `course_modules.id`, `userid`, `completionstate` (0 incomplete, 1 complete, 2 complete pass, 3 complete fail), `overrideby`, `timemodified` | No row = incomplete. **Unique** (`userid`, `coursemoduleid`). |
| `course_completion_criteria` | `id`, `course`, `criteriatype` (1 self … 8 course), `module`, `moduleinstance`, `courseinstance` (type 8: the other course), `enrolperiod`, `timeend`, `gradepass`, `role` | One row per criterion. The meaning of the columns depends on `criteriatype`. |
| `course_completion_aggr_methd` | `id`, `course`, `criteriatype` (null = overall), `method` (1 ALL, 2 ANY), `value` | **Unique** (`course`, `criteriatype`). No row = ALL. |
| `course_completion_crit_compl` | `id`, `userid`, `course`, `criteriaid` → `course_completion_criteria.id`, `gradefinal`, `unenroled`, `timecompleted` | **Unique** (`userid`, `course`, `criteriaid`). One row per criterion the user has met. |
| `course_completions` | `id`, `userid`, `course`, `timeenrolled`, `timestarted`, `timecompleted` (null = not complete), `reaggregate` | **Unique** (`userid`, `course`). `reaggregate > 0` tells `completion_regular_task` to recalculate. |

New system (#6): `adv_course`, `adv_student`, `adv_prerequisite` (`course`, `prerequisite`, primary key on both, a course cannot require itself) and `adv_course_completion` (`username`, `course`, `time_completed`), see `backend/src/main/resources/db/migration`.

## Observations for the new system
- **"Complete" excludes "complete and failed".** student3 submitted Assignment 1 and got 40 (pass grade 50). For Moodle the activity is complete but failed, so a "must be complete" restriction stays closed, while an "incomplete" restriction opens. Our completion states must keep these four values.
- **Course completion lags.** Right after the sample data is loaded, CS201's "Completion of other courses: CS101" criterion is still unmet for student1 and student2 until `completion_regular_task` runs (also noted in `tests/golden-master/README.md`). The new system should update completion when the grade is saved.
- **Boundaries are not symmetric.** Date "from" and grade "minimum" are inclusive, date "until" and grade "maximum" are exclusive. Golden master tests must check both edges.
- **Missing activities fail closed.** A restriction on a deleted activity is never met, even when negated. This is safe behaviour worth keeping.
- **Restrictions are enforced on the server.** Opening a restricted item by its URL fails, not only the link in the UI.

## Presentation slide (Nov 25)
**Advising: what Moodle does today, and what it cannot do**
- Moodle has no advising module; the closest rules are access restrictions and course completion
- 8 preserved requirements (`REQ-ADV-01`…`08`), 39 scenarios checked in a running Moodle 5.3.1, all as documented
- Restrictions: AND / OR / NOT trees of completion, date and grade conditions, with exact edges (from ≥, until <, min ≥, max <)
- "Completion of other courses" only decides completion: student3 is in CS201 without passing CS101
- MDL-22648: requested since 2012, 99 votes, still open, because enrolment is spread over plugins
- Our answer (`REQ-ADV-09`): one prerequisite check in the advising module, live demo `GET /api/advising/prerequisite-check`
