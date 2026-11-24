# Courses and enrolment, Moodle 5.3.1

**Enrolment = membership + role.** A user is enrolled through an enrolment method (manual, self, …) and gets a role in the course (student, teacher). Access needs an **active** enrolment: not suspended, method enabled, start time passed, end time not reached.

| Rule | Moodle result |
|---|---|
| Duplicate course short name `CS101` | refused ("Short name is already used") |
| Short name `cs101` next to `CS101` | **accepted** (check is case-sensitive) |
| Course end date before start date | refused; end = start is allowed |
| Teacher enrols users | can give *student* or *non-editing teacher*, not *editing teacher* |
| Self enrolment, limit 2, third user | refused ("Maximum number … reached") |
| Enrolment ending before it starts | **accepted, but never active** |

**45 checks run against the live Docker Moodle, all passed.** Details: [course requirements](../srs/modules/course.md) (`REQ-CRS-01`…`09`).

**Gaps found → new system (REQ-CRS-09):** course short name and ID number are unique only in PHP, not in the database; enrolments with end before start are stored; prerequisites are not checked at enrolment (student3 is in CS201 without CS101, MDL-22648 → advising).

*Rule for the new system: uniqueness and date rules are enforced in the database and the service, not only in the form.*
