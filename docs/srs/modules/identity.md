# Identity and access rules

Moodle baseline: 5.3.1 (`v5.3.1`, commit `cfe123f2`). Moodle source paths below are relative to its `public/` directory.

## Requirements

| ID | Type | Requirement and acceptance criteria | Moodle source |
|---|---|---|---|
| REQ-IDN-01 | Preserved | Authenticate through the user's enabled authentication plugin. Invalid credentials, suspended users, and invalid login tokens must not establish an authenticated session; a successful login must complete the Moodle login flow. | [`login/index.php`](https://github.com/moodle/moodle/blob/v5.3.1/public/login/index.php#L154), `authenticate_user_login()` and `complete_user_login()` in [`lib/moodlelib.php`](https://github.com/moodle/moodle/blob/v5.3.1/public/lib/moodlelib.php#L3795), [`auth/manual/auth.php`](https://github.com/moodle/moodle/blob/v5.3.1/public/auth/manual/auth.php#L64) |
| REQ-IDN-02 | Preserved | Assign the project roles student → `student`, teacher → `editingteacher`, and admin → `manager`. Role membership is scoped to a Moodle context; the built-in site administrator is a special superuser and is not a substitute for an ordinary `manager` role assignment. | `role_assign()` and `get_user_roles()` in [`lib/accesslib.php`](https://github.com/moodle/moodle/blob/v5.3.1/public/lib/accesslib.php#L1579), [`lib/classes/context/`](https://github.com/moodle/moodle/tree/v5.3.1/public/lib/classes/context), role archetypes in [`lib/db/access.php`](https://github.com/moodle/moodle/blob/v5.3.1/public/lib/db/access.php#L857) |
| REQ-IDN-03 | Preserved | A user may view a course only when enrolled or otherwise explicitly permitted. Changing a course or module ID in a URL must not grant access to another course. | `require_login()` in [`course/view.php`](https://github.com/moodle/moodle/blob/v5.3.1/public/course/view.php), course and module contexts in [`lib/classes/context/`](https://github.com/moodle/moodle/tree/v5.3.1/public/lib/classes/context) |
| REQ-IDN-04 | Preserved | Enforce each action using its capability at the relevant context; direct requests must receive the same checks as UI navigation. Students cannot create activities or grade submissions; editing teachers and managers can. | `has_capability()`, `require_capability()`, `get_user_roles()` in [`lib/accesslib.php`](https://github.com/moodle/moodle/blob/v5.3.1/public/lib/accesslib.php#L432); `moodle/course:manageactivities` in [`lib/db/access.php`](https://github.com/moodle/moodle/blob/v5.3.1/public/lib/db/access.php#L983); `mod/assign:grade` in [`mod/assign/db/access.php`](https://github.com/moodle/moodle/blob/v5.3.1/public/mod/assign/db/access.php#L49) |
| REQ-IDN-05 | Preserved | Only a user with `enrol/manual:enrol` in the course context may enrol users through manual enrolment. | [`enrol/manual/db/access.php`](https://github.com/moodle/moodle/blob/v5.3.1/public/enrol/manual/db/access.php#L39), `require_capability()` in [`enrol/manual/manage.php`](https://github.com/moodle/moodle/blob/v5.3.1/public/enrol/manual/manage.php#L47) |
| REQ-IDN-06 | Preserved | Record failed authentication attempts. Account lockout is applied only when its configured threshold is non-zero; when enabled, the configured window and duration govern lockout. | `authenticate_user_login()` in [`lib/moodlelib.php`](https://github.com/moodle/moodle/blob/v5.3.1/public/lib/moodlelib.php#L3795), `login_attempt_failed()` and `login_is_lockedout()` in [`lib/authlib.php`](https://github.com/moodle/moodle/blob/v5.3.1/public/lib/authlib.php#L905), settings in [`admin/settings/security.php`](https://github.com/moodle/moodle/blob/v5.3.1/public/admin/settings/security.php#L89) |
| REQ-IDN-07 | Preserved | Apply Moodle's password policy when passwords are set and enforce the configured inactive-session timeout. | [`admin/settings/security.php`](https://github.com/moodle/moodle/blob/v5.3.1/public/admin/settings/security.php#L93), [`admin/settings/server.php`](https://github.com/moodle/moodle/blob/v5.3.1/public/admin/settings/server.php#L88), `check_password_policy()` in [`lib/moodlelib.php`](https://github.com/moodle/moodle/blob/v5.3.1/public/lib/moodlelib.php#L4427) |

## Role and permission baseline

The table was checked against the sample data loaded in the local Docker Moodle on 2026-11-22. “Admin” in the runtime results is the bootstrap site administrator; Moodle treats that account as a special superuser. The project mapping remains the ordinary `manager` archetype, whose default permissions are defined in Moodle's role capability files.

| Action | Student (`student1`) | Teacher (`teacher1`) | Manager role | Site admin (`admin`) | Runtime evidence |
|---|---|---|---|---|---|
| View an enrolled course | Allowed | Allowed | Allowed | Allowed | `student1`, `teacher1`, and `teacher1` temporarily assigned the manager role opened CS101; each page returned HTTP 200. |
| Open a course without enrolment | Denied | Not tested | Not tested | Allowed as site administrator | `student5` requesting CS201 by direct URL was redirected to the enrolment page; CS201 content was not served. |
| View an assignment | Allowed | Allowed | Allowed | Allowed | `mod/assign:view` was true for all four role configurations in the runtime capability checks. |
| Create an assignment | Denied | Allowed | Allowed | Allowed | `moodle/course:manageactivities` was false for student and true for teacher, manager, and site admin. |
| Grade an assignment | Denied | Allowed | Allowed | Allowed | Student's grading URL returned HTTP 404; teacher and manager sessions opened the same submissions page with HTTP 200. `mod/assign:grade` was false for student and true for teacher, manager, and site admin. |
| Enrol users manually | Denied | Allowed | Allowed | Allowed | `enrol/manual:enrol` was false for student and true for teacher, manager, and site admin. |
| Assign course roles | Denied | Allowed | Allowed | Allowed | `moodle/role:assign` was false for student and true for teacher, manager, and site admin. |

The manager check used a normal, non-site-admin account: `teacher1`'s CS101 role assignment was temporarily changed from `editingteacher` to `manager`, then restored. That user logged in and opened both CS101 and the assignment grading page. `moodle/course:view` is not the permission that granted enrolled sample users their course pages; course access was checked through enrolment and `require_login()`.

## Authentication settings observed

The following values were read from the fresh local Moodle 5.3.1 sample instance:

| Setting | Observed value |
|---|---|
| Invalid password | Does not authenticate; the login flow returns to the login page. |
| Failed-login lockout threshold | `0` (disabled); configured failure-count window is 1,800 seconds and lockout duration is 1,800 seconds. |
| Password policy | Enabled; minimum 8 characters, including at least one digit, lowercase letter, uppercase letter, and non-alphanumeric character. |
| Enforce password policy at login | Disabled (`passwordpolicycheckonlogin = 0`). |
| Inactive session timeout | 28,800 seconds (8 hours). This is the configured timeout; expiry was not measured by waiting for a session to lapse. |

## Decisions in the new system

- **Accounts live in the database** (`idn_user`); login works with HTTP Basic (for tools and the golden master) and with `POST /api/identity/login`, which opens a session. Every failure answers 401 `INVALID_LOGIN` "Invalid login, please try again", so the answer does not tell whether the user exists.
- **Settings are the KPI targets** (`fig.identity.*`): lockout after 5 failures within 15 minutes for 15 minutes (REQ-IDN-06, Moodle's algorithm with a non-zero threshold), session timeout 2 hours (REQ-IDN-07), password at least 12 characters with a digit, a lower-case letter, an upper-case letter and a non-alphanumeric character.
- **Sample accounts** are seeded without a password and get `SAMPLE_USER_PASSWORD` at startup; with the variable empty nobody can log in as them.
- **Roles** are the four Moodle archetypes `student`, `teacher`, `editingteacher`, `manager`; capabilities are a fixed table mirroring Moodle's `access.php` defaults (`identity.api.Capability`). Role overrides per context are not implemented.

## Data model

Table and column names are from Moodle 5.3.1 [`lib/db/install.xml`](https://github.com/moodle/moodle/blob/v5.3.1/public/lib/db/install.xml).

| Table | Identity/access fields | Purpose |
|---|---|---|
| `user` | `id`, `auth`, `username`, `password`, `confirmed`, `suspended`, `deleted`, `mnethostid` | Local or external account identity and login state. |
| `role` | `id`, `shortname`, `name`, `archetype` | Role definitions, including `student`, `editingteacher`, and `manager`. |
| `context` | `id`, `contextlevel`, `instanceid`, `path`, `depth` | System, category, course, and activity scope; parent paths support inherited access checks. |
| `role_assignments` | `roleid`, `contextid`, `userid`, `component`, `itemid` | Assigns a role to a user at a context, optionally on behalf of an enrolment/authentication plugin. |
| `role_capabilities` | `contextid`, `roleid`, `capability`, `permission` | Stores context-specific capability overrides (`CAP_ALLOW`, `CAP_PREVENT`, or `CAP_PROHIBIT`). |

## Verification notes

- The local Compose stack was built from the repository's Moodle `v5.3.1` image, returned HTTP 200 on the login page, and loaded the sample courses/users.
- Successful login was confirmed for `student1`, `teacher1`, and the bootstrap `admin`. A wrong password did not authenticate.
- Direct course and assignment URLs and effective capabilities were checked as described above. The stack and sample-data setup are documented in [`infra/README.md`](../../../infra/README.md).
