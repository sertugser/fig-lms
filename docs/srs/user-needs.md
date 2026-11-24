# User needs

| Owner | Issue | Status |
|---|---|---|
| @sertugser | #20 | Survey ready to send; **no answers yet** |

Goal: find the main pain points of students and teachers with Moodle in our six modules, and link each top need to a requirement ID (or a new feature). Target: at least 10 students and 2–3 teachers.

## How to run the survey
1. Create a Google Form with the questions below (copy them as they are; questions 1–2 decide which branch the person sees).
2. Share the link in the course group and with 2–3 teachers who use Moodle. Leave it open for at least 3 days.
3. Export the answers as CSV to `docs/srs/user-needs/responses.csv` **without names or e-mail addresses** (turn off "collect e-mail addresses" in the form).
4. Fill in the results tables below and link each top need to a requirement.

## Questions (8)

| # | Question | Type | Linked module |
|---|---|---|---|
| 1 | Are you a student or a teacher? | Single choice: student / teacher | — |
| 2 | How often do you use Moodle? | Single choice: daily / a few times a week / weekly / less | — |
| 3 | Rate these tasks from 1 (very hard) to 5 (very easy): logging in; finding and enrolling in a course; submitting an assignment (students) or creating an assignment (teachers); seeing your grades (students) or grading (teachers). | Grid, 1–5 | IDN, CRS, ASG, GRD |
| 4 | Have you ever enrolled in (or been put into) a course before you had completed the course it builds on? What happened? | Yes / no + short text | ADV (REQ-ADV-08/09) |
| 5 | Students: when you plan your next term, how do you decide which courses you are allowed and able to take (prerequisites, credit limit)? Teachers/advisors: how do you check this for a student? | Short text | ADV, PLN |
| 6 | Have you had a problem with assignment deadlines, late submissions, drafts or extra attempts? Please describe one case. | Short text | ASG (REQ-ASG-02, 04, 06, 08) |
| 7 | Is anything about how grades are calculated or shown unclear to you (weights, hidden grades, totals)? | Short text | GRD (REQ-GRD-02, 03, 07) |
| 8 | If you could change one thing in Moodle for these tasks, what would it be? | Short text | all |

## Results

*To be filled in when the answers are in.*

| Group | Answers |
|---|---:|
| Students | — |
| Teachers | — |

### Task ratings (question 3, mean 1–5)
| Task | Students | Teachers |
|---|---:|---:|
| Logging in | — | — |
| Finding and enrolling in a course | — | — |
| Submitting / creating an assignment | — | — |
| Seeing own grades / grading | — | — |

### Top needs
| # | Need (in the users' words, summarised) | Mentioned by (n) | Requirement or new feature |
|---|---|---:|---|
| 1 | | | |
| 2 | | | |
| 3 | | | |
| 4 | | | |
| 5 | | | |

## Related findings we already have
These come from our own measurements, not from the survey, and help to check whether the survey confirms them:
- Self-enrolment: a tester found no visible course catalogue from the Dashboard ([usability.md](../kpi/usability.md), FLOW-05).
- Prerequisites: Moodle lets a student into CS201 without completing CS101 ([advising.md](modules/advising.md), REQ-ADV-08); MDL-22648 has been open since 2012 with 99 votes.
- Assignments: reverting a submission to draft can grant an extra attempt (MDL-87490, REQ-ASG-08).
