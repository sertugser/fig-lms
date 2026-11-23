# Module: Courses & enrolment (`REQ-CRS`)

| Owner | Issue | Moodle version | Status |
|---|---|---|---|
| @efekanegeli2 | #9 | 5.3.1 (`v5.3.1`, Build 20261112) | Draft for review |

**Scope:** course creation, course categories, teacher assignment and student enrolment (manual and self enrolment). Guest access, cohort sync, meta links, payments and external enrolment plugins (LDAP, database) are out of scope.

**How the rules were found:** by reading the Moodle source (paths below are relative to `public/`) and then running each rule against the Docker Moodle from #8:
- [`course/verify_rules.php`](course/verify_rules.php) calls the real Moodle functions and checks 45 scenarios: **45 passed**, see [`course/verify_results.txt`](course/verify_results.txt). The script rolls back everything it creates, so it can be run again at any time.
- Screenshots of the same rules in the web UI are in [`course/screenshots/`](course/screenshots/).

Requirement row format follows the SRS convention from #3: ID, type (preserved / bug fix / new), description, Moodle source, acceptance criteria, owner.

## Terms
| Term | Meaning in Moodle |
|---|---|
| Category | Folder in a tree that holds courses and subcategories (`course_categories`). |
| Enrolment method (instance) | One way into one course, e.g. "manual" or "self" (a row in `enrol`). A course can have several. |
| User enrolment | A user's membership through one enrolment method (a row in `user_enrolments`), with a status and an optional time window. |
| Role assignment | What the user may do in the course (student, teacher, ...). It is stored separately from the enrolment (`role_assignments`). |
| Active enrolment | An enrolment that currently gives access; see REQ-CRS-06. |

## Requirements

| ID | Type | Description | Moodle source | Acceptance criteria | Owner |
|---|---|---|---|---|---|
| REQ-CRS-01 | preserved | **Course identity.** A course must have a full name (max 1333 chars), a short name (max 255) and an existing category. The short name must be unique across the site. The ID number (max 100) is optional, but must be unique when it is not empty. | `course/lib.php` `create_course()`; `course/edit_form.php` `definition()`, `validation()`; `course/classes/constants.php` | 1. Duplicate short name is rejected with "Short name is already used for another course (…)". 2. Duplicate non-empty ID number is rejected. 3. Two courses with an empty ID number are allowed. 4. A non-existent category is rejected. 5. Uniqueness is **case-sensitive**: `cs101` is accepted next to `CS101` (observed). | @efekanegeli2 |
| REQ-CRS-02 | preserved | **Course dates.** Start date is required. End date is optional (0 = none). If both are set, the end date must not be before the start date; equal dates are allowed. An end date without a start date is rejected. The form proposes start = tomorrow 00:00 and end = start + 1 year (`moodlecourse/courseduration`). | `course/lib.php` `course_validate_dates()`; `course/edit_form.php` | 1. end < start → "The course end date must be after the start date." 2. end = start → accepted. 3. end set, start 0 → rejected (`nostartdatenoenddate`). 4. end 0 → accepted. | @efekanegeli2 |
| REQ-CRS-03 | preserved | **Who creates courses, and what a new course gets.** Creating a course needs `moodle/course:create` in the target category (manager and course creator by default; teacher and student do not have it). The creator is enrolled with the role in `creatornewroleid` (editing teacher) if they could not already view the course; admins are enrolled only when `enroladminnewcourse` is on. A new course gets the default enrolment methods: manual (enabled), guest and self (disabled). If visibility is not given, the course takes the category's visibility. | `course/edit.php` (capability check, `enrol_try_internal_enrol()`); `course/lib.php` `create_course()` → `enrol_course_updated()`; `lib/enrollib.php` `add_default_instance()` | 1. student1 and teacher1 lack `moodle/course:create` in Engineering; admin has it. 2. A new course has `manual` enabled, `guest` and `self` disabled. 3. A course created in a hidden category is hidden. | @efekanegeli2 |
| REQ-CRS-04 | preserved | **Categories.** Name is required (max 255) and does not need to be unique. The ID number is optional (max 100), but must be unique when not empty. Categories form a tree (`parent`, `depth`, `path`). A category under a hidden parent is hidden. A category cannot be moved into itself or into one of its subcategories. Managing categories needs `moodle/category:manage`. A non-empty category can be deleted only by moving its content elsewhere, or by deleting all of it (needs manage rights on every subcategory and course). | `course/classes/category.php` `create()`, `change_parent_raw()`, `can_delete_full()`, `can_move_content_to()` | 1. Empty name → "Category name is required". 2. 255-char name accepted, 256 rejected. 3. Duplicate ID number → "ID number is already used for another category". 4. Child of hidden parent has `visible = 0`. 5. Moving a parent under its own child → `cannotmovecategory`. 6. teacher1 lacks `moodle/category:manage`. | @efekanegeli2 |
| REQ-CRS-05 | preserved | **Enrolling a user.** Enrolment creates one `user_enrolments` row per (method, user) and assigns the given role in the course context. Enrolling the same user again through the same method updates the existing row (status and times) instead of adding a second one. Users cannot be enrolled in the site front page, and the guest user is never enrolled. | `lib/enrollib.php` `enrol_plugin::enrol_user()`, `is_enrolled()`; `enrol/manual/externallib.php` `enrol_users()` | 1. Manual enrolment as student → active enrolment and a student role in the course. 2. Second enrolment of the same user → still 1 row. 3. Enrolment into the front page → error. 4. `is_enrolled(guest)` is false. | @efekanegeli2 |
| REQ-CRS-06 | preserved | **Active enrolment.** An enrolment gives access only if **all** of these hold: the user enrolment status is active (not suspended), the enrolment method is enabled, `timestart < now` (strictly), and `timeend = 0 or timeend > now`. Otherwise the user is still "enrolled" but has no access. | `lib/enrollib.php` `get_enrolled_join()`, `enrol_get_enrolment_end()`, `is_enrolled(..., $onlyactive)` | 1. Start in the future → not active. 2. End in the past → not active. 3. Start in the past, end in the future → active. 4. Suspended → not active. 5. Method disabled → none of its users is active. | @efekanegeli2 |
| REQ-CRS-07 | preserved | **Teacher assignment.** A teacher is a user enrolled in the course with the role *editing teacher* (or *non-editing teacher*). Editing teachers are shown as course contacts (`coursecontact`). To enrol others you need `enrol/manual:enrol` (editing teacher and manager have it, students do not). You can assign only the roles you are allowed to assign: an editing teacher can assign *non-editing teacher* and *student*, but **not** *editing teacher*. | `lib/enrollib.php`; `enrol/manual/externallib.php` `enrol_users()` (`require_capability`, `get_assignable_roles()`); `course/classes/list_element.php` `get_course_contacts()` | 1. teacher1 enrolled as editing teacher appears as "Teacher: Ayse Yilmaz" on the course. 2. student1 lacks `enrol/manual:enrol`; teacher1 has it. 3. teacher1's assignable roles are exactly "Non-editing teacher, Student". | @efekanegeli2 |
| REQ-CRS-08 | preserved | **Self enrolment.** A user may enrol themself only if **all** of these hold: they are not the guest user; they are not already enrolled through this method; the method is enabled and allows new enrolments; now is between the enrolment start and end dates (when set); the maximum number of enrolled users is not reached; they are in the required cohort (if set); they have `enrol/self:enrolself`; and they enter the correct enrolment key (if set). The maximum counts **all** users enrolled through the method, including suspended ones. The enrolment duration sets `timeend = timestart + enrolperiod`. | `enrol/self/lib.php` `can_self_enrol()`, `is_self_enrol_available()`, `enrol_self()`; `enrol/self/classes/form/enrol_form.php` `validation()` | 1. Allowed user with a 30-day period → enrolled, `timeend − timestart = 30 days`. 2. Max 2: 2nd user allowed, 3rd gets "Maximum number of users allowed to self-enrol was already reached." 3. Before start date → "You cannot enrol yet; enrolment starts on …". 4. After end date → "You cannot enrol any more, since enrolment ended on …". 5. New enrolments off or already enrolled → "Enrolment is disabled or inactive". 6. Guest → "Guests cannot access this course." 7. Wrong key → "Incorrect enrolment key, please try again". | @efekanegeli2 |
| REQ-CRS-09 | new | **Data integrity the new system adds.** (a) Course short name and ID number, and category ID number, are unique in the **database** (Moodle checks them only in PHP, so two parallel requests can create duplicates). (b) An enrolment with `timeend ≤ timestart` is rejected (Moodle stores it, and the enrolment can never become active). (c) Team decision needed: should short name uniqueness be case-insensitive? | Gap found in `lib/db/install.xml` (indexes on `course.shortname` and `course.idnumber` are not unique) and `lib/enrollib.php` `enrol_user()` (no time check) | 1. A DB constraint rejects a duplicate short name even without the service-layer check. 2. Enrolling with end before start returns a validation error. 3. Decision on (c) recorded in this file. | @efekanegeli2 |

## Golden master scenarios

Each scenario has the same input for Moodle and for the new system, and the result must match. Exception: REQ-CRS-09 deliberately changes Moodle's behaviour. "Script" refers to lines in [`course/verify_results.txt`](course/verify_results.txt).

| Requirement | Normal | Boundary | Invalid input | Unauthorized access |
|---|---|---|---|---|
| REQ-CRS-01 | Create `VER-1` in Engineering → created | `cs101` next to `CS101` → created (case-sensitive); two empty ID numbers → both created | Short name `CS101` → `shortnametaken`; ID number `CS101` → `courseidnumbertaken`; category 999999 → not found | student1 opens `course/edit.php?category=2` → no permission (`moodle/course:create`) |
| REQ-CRS-02 | End date 0 → created | End = start → created | End = start − 1 s → `enddatebeforestartdate`; end without start → `nostartdatenoenddate` | (same capability as REQ-CRS-01) |
| REQ-CRS-03 | admin creates a course → manual (enabled), guest and self (disabled) | Course in a hidden category → `visible = 0` | n/a | student1 and teacher1 have no `moodle/course:create` in Engineering |
| REQ-CRS-04 | Subcategory of Engineering → depth 2, path `/2/<id>` | Name 255 chars → created; 256 → `categorytoolong` | Empty name → `categorynamerequired`; ID number `FIG-ENG` → `categoryidnumbertaken`; move into own child → `cannotmovecategory` | teacher1 has no `moodle/category:manage` |
| REQ-CRS-05 | Manual enrol student1 → active + student role | Enrol student1 twice → 1 row | Front page → `codingerror`; guest → never enrolled | student1 has no `enrol/manual:enrol` |
| REQ-CRS-06 | Window (now − 1 d, now + 1 d) → active | Start = now + 1 d → not active; end = now − 1 d → not active | End before start → stored but never active (see REQ-CRS-09) | Suspended, or method disabled → not active |
| REQ-CRS-07 | teacher1 as editing teacher → course contact | Assignable roles for teacher1 = non-editing teacher, student | Role not assignable → `wsusercannotassign` | student1 has no `enrol/manual:enrol` |
| REQ-CRS-08 | Self enrol with 30-day period → `timeend = timestart + 30 d` | Max 2: 2nd allowed, 3rd rejected | Wrong key; before start; after end; new enrolments off; already enrolled | Guest user → rejected |

For #12 / #13: the read-only REST service can observe the results with `core_course_get_courses`, `core_course_get_categories`, `core_enrol_get_enrolled_users` and `core_enrol_get_course_enrolment_methods`. Scenarios that create data need the write functions (`core_course_create_courses`, `core_course_create_categories`, `enrol_manual_enrol_users`, `enrol_self_enrol_user`), which are not yet in the `fig_golden_master` service; for now, run `verify_rules.php`.

## Data model

From `lib/db/install.xml`. Only the columns these rules use are listed.

| Table | Key columns | Constraints and notes |
|---|---|---|
| `course` | `id`, `category` → `course_categories.id`, `fullname` (1333), `shortname` (255), `idnumber` (100), `startdate`, `enddate` (0 = none), `visible`, `format`, `enablecompletion` | Indexes on `shortname`, `idnumber` and `category` are **not unique**; uniqueness is only checked in PHP (REQ-CRS-09). `id = 1` is the site front page. |
| `course_categories` | `id`, `name` (255), `idnumber` (100, nullable), `parent` → `course_categories.id` (0 = top), `depth`, `path` (e.g. `/2/5`), `visible`, `visibleold`, `coursecount` | No unique index on `idnumber`; checked in PHP. |
| `enrol` | `id`, `courseid` → `course.id`, `enrol` (plugin: `manual`, `self`, …), `status` (0 enabled, 1 disabled), `roleid` → `role.id` (default role), `enrolperiod`, `enrolstartdate`, `enrolenddate`, `password` (self enrolment key), `customint3` (self: max enrolled), `customint5` (self: cohort), `customint6` (self: allow new enrolments) | One row per enrolment method per course. The meaning of `customint*` depends on the plugin. |
| `user_enrolments` | `id`, `enrolid` → `enrol.id`, `userid` → `user.id`, `status` (0 active, 1 suspended), `timestart`, `timeend` (0 or 2147483647 = no end), `modifierid` | **Unique** index on (`enrolid`, `userid`). |
| `role_assignments` (identity module, #7) | `roleid`, `contextid` (course context), `userid`, `component`, `itemid` | The role a user has in the course. The enrolment and the role are stored separately. |

## Observations for the new system
- **Prerequisites are not checked at enrolment.** In the sample data, student3 is enrolled in CS201 without having completed CS101. Moodle allows this, which is the gap MDL-22648 describes; the advising module (#4, #6) handles it.
- **Uniqueness depends on PHP checks only** (see REQ-CRS-09). Moodle's own comparison is case-sensitive on PostgreSQL.
- **An enrolment with end before start** is saved and silently never becomes active. No error is shown when it is created through the API.
- **The self-enrolment limit also counts suspended users**, so suspending users does not free up places.
- **"Enrolled" and "active" are different things** in Moodle (`is_enrolled($onlyactive = false/true)`). Reports and the golden master tests have to say which one they compare.

## Screenshots (Moodle 5.3.1, Docker setup from #8)
| File | Rule |
|---|---|
| [01-course-duplicate-shortname.png](course/screenshots/01-course-duplicate-shortname.png) | REQ-CRS-01 duplicate short name |
| [02-course-enddate-before-startdate.png](course/screenshots/02-course-enddate-before-startdate.png) | REQ-CRS-02 end before start |
| [03-category-duplicate-idnumber.png](course/screenshots/03-category-duplicate-idnumber.png) | REQ-CRS-04 duplicate category ID number |
| [04-category-courses.png](course/screenshots/04-category-courses.png) | REQ-CRS-04 category with its courses (management view) |
| [05-cs101-participants-roles.png](course/screenshots/05-cs101-participants-roles.png) | REQ-CRS-05/07 enrolled users with roles and status |
| [06-teacher-enrol-dialog-roles.png](course/screenshots/06-teacher-enrol-dialog-roles.png) | REQ-CRS-07 teacher's enrol dialog (roles: non-editing teacher, student) |
| [07-not-enrolled-student-cs201.png](course/screenshots/07-not-enrolled-student-cs201.png) | REQ-CRS-05/07 student5 not enrolled in CS201: "You cannot enrol yourself in this course."; teacher shown as contact |
| [08-self-enrol-wrong-key.png](course/screenshots/08-self-enrol-wrong-key.png) | REQ-CRS-08 wrong enrolment key |
| [09-self-enrol-max-reached.png](course/screenshots/09-self-enrol-max-reached.png) | REQ-CRS-08 maximum enrolled users reached |

Screenshots 08 and 09 used two temporary demo courses, which were deleted afterwards. The sample data is unchanged.

## Presentation slide (Nov 25)
**Courses & enrolment: what Moodle does today**
- 9 requirements (`REQ-CRS-01`…`09`): 8 preserved, 1 new; 45 scenarios checked in a running Moodle 5.3.1, all as documented
- Course: unique short name (case-sensitive), end date ≥ start date, created only with `moodle/course:create`
- Enrolment = membership (`user_enrolments`) + role; access only while active: not suspended, method on, inside the time window
- Self enrolment: 8 conditions (key, capacity, dates, cohort, …)
- Found gaps: no DB uniqueness, end-before-start enrolments accepted, prerequisites not checked (MDL-22648) → REQ-CRS-09 and the advising module
