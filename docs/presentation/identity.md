# Identity and access — Moodle 5.3.1

**Three project roles:** student → `student` · teacher → `editingteacher` · admin → `manager`

| Course action | Student | Teacher | Admin |
|---|---:|---:|---:|
| View enrolled course | Yes | Yes | Yes |
| Create assignment | No | Yes | Yes |
| Grade submissions | No | Yes | Yes |
| Enrol users | No | Yes | Yes |

**Security baseline:** enrolled student reached CS101; a student changing the course ID to CS201 was redirected to enrolment. Direct student grading was denied; the teacher grading page worked. Invalid password did not create a session.

**Current Moodle settings:** password policy on (8 characters + four character classes); account lockout off (threshold 0); session timeout 8 hours.

*Permission rule: enforce capabilities at course/activity context on every request, not only in the UI.*

Source and test details: [identity requirements](../srs/modules/identity.md). The Docker bootstrap `admin` is a special site administrator; the configured project role mapping is the normal `manager` role.
