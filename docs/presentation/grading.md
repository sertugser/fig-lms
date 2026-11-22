# Grading: weighted mean and who sees which grade, Moodle 5.3.1

**Weighted mean:** total = sum(weight × grade) / sum(weight), grades first scaled to 0..1. A=80 (weight 1), B=40 (weight 3) → **50**.

| Rule | Moodle result |
|---|---|
| Empty grade excluded (default) / counted as 0 | 50 / 33.3 (weighted mean) · 60 / 40 (mean) |
| Drop lowest 1 | 80 (mean of 80 and 40, lowest dropped) |
| Grade above maximum / below minimum | stored as the maximum / minimum |
| Student asks for another student's grades | refused (`nopermissiontoviewgrades`) |
| Item hidden | student does not see it |

**18 checks run against the live Docker Moodle, all passed.** Details: [grading requirements](../srs/modules/grading.md).

**Bug MDL-85503 (live demo):** with `moodle/category:viewcourselist` prevented, a student sees **no categories** (HTTP 404), the admin sees all. Run `python docs/bugs/MDL-85503/reproduce.py`. Note: the ticket is about course categories, not grading ([bug report](../bugs/MDL-85503.md)).

*Rule for the new system: grades are limited to the item's range; a student reads only their own grades.*
