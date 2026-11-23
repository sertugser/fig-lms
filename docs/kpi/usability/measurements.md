# Moodle usability interaction records

**Collection:** Five successful, separate headless Playwright-guided browser runs on Moodle 5.3.1, 22 November 2026. Actions were selected after inspecting the browser. The document column uses committed Chromium main-frame navigations. Authentication, Dashboard preparation, fixture work, and readiness checks are excluded. Each field ID is unique within its flow.

## FLOW-01

**Role and start:** `student5` (student), Dashboard.

**Required fixture:** In `UX16-BASELINE`, `student5` has the student role. **Issue 16 submit a response** is a visible individual assignment with online text enabled, no opening, due, cut-off or time-limit gates, explicit submission enabled, and no required statement. The assignment has no attempt for this user. Use the text `Issue 16 sample submission.`

**Finish:** Submit through the assignment page. Stop when the UI shows **Submitted for grading**.

| Step | Visible action | Click + | Step + | Document + | Field ID | Resulting state | Observation |
| ---: | --- | ---: | ---: | ---: | --- | --- | --- |
| 1 | Issue 16 Usability Baseline | 1 | 1 | 1 | — | Course (/course/view.php) | — |
| 2 | Issue 16 submit a response | 1 | 1 | 1 | — | Assignment with Add submission (/mod/assign/view.php) | — |
| 3 | Add submission | 1 | 1 | 1 | — | Online text editor (/mod/assign/view.php) | — |
| 4 | Enter sample response in Online text | 0 | 1 | 0 | online-text | Response entered | — |
| 5 | Save changes | 1 | 1 | 1 | — | Draft with Submit assignment (/mod/assign/view.php) | — |
| 6 | Submit assignment | 1 | 1 | 1 | — | Confirm submission form (/mod/assign/view.php) | Saving had left the submission as a draft. |
| 7 | Continue | 1 | 1 | 1 | — | Submitted for grading (/mod/assign/view.php) | Explicit confirmation is required after saving and selecting Submit assignment. |

**Verified finish:** Submission status was Submitted for grading. [Browser capture](flow-01-submitted.png).

**Totals:** 6 clicks, 7 steps, 6 document loads, 1 fields.

## FLOW-02

**Role and start:** `student1` (student), Dashboard.

**Required fixture:** Reuse CS101 **Assignment 1**. Read-only data confirms the gradebook contains an unhidden grade of 85.

**Finish:** Open the student's grade view through the course UI. Stop when their own grade for **Assignment 1** is visible.

| Step | Visible action | Click + | Step + | Document + | Field ID | Resulting state | Observation |
| ---: | --- | ---: | ---: | ---: | --- | --- | --- |
| 1 | Introduction to Programming | 1 | 1 | 1 | — | Course with navigation tour (/course/view.php) | — |
| 2 | Got it | 1 | 1 | 0 | — | Course navigation available | Course tour overlaid the Grades navigation. |
| 3 | Grades | 1 | 1 | 1 | — | Own User report with grade 85.00 (/grade/report/user/index.php) | — |

**Verified finish:** Own User report showed Assignment 1 grade 85.00 / 100. [Browser capture](flow-02-grade.png).

**Totals:** 3 clicks, 3 steps, 2 document loads, 0 fields.

## FLOW-03

**Role and start:** `teacher1` (editing teacher), Dashboard.

**Required fixture:** `teacher1` is an editing teacher in `UX16-BASELINE`. Start with Edit mode off and no activity of the chosen name. Use the name `Issue 16 assignment creation` with a unique suffix on subsequent runs. Keep form defaults unless the UI requires a change.

**Finish:** Add an Assignment through the visible course controls and save. Stop when the named activity is visible.

| Step | Visible action | Click + | Step + | Document + | Field ID | Resulting state | Observation |
| ---: | --- | ---: | ---: | ---: | --- | --- | --- |
| 1 | Issue 16 Usability Baseline | 1 | 1 | 1 | — | Course with teacher tour (/course/view.php) | — |
| 2 | Skip tour | 1 | 1 | 0 | — | Course controls available | Teacher course tour overlaid Edit mode. |
| 3 | Turn on Edit mode | 1 | 1 | 0 | — | Edit mode enabled | — |
| 4 | Add content in New section | 1 | 1 | 0 | — | Section add menu | — |
| 5 | Activity or resource | 1 | 1 | 0 | — | Activity chooser | — |
| 6 | Assignment | 1 | 1 | 0 | — | Assignment selected in chooser | — |
| 7 | Add | 1 | 1 | 1 | — | Assignment creation form (/course/modedit.php) | Selecting Assignment required a separate Add confirmation in the chooser. |
| 8 | Enter Assignment name | 0 | 1 | 0 | assignment-name | Name entered | — |
| 9 | Save and display | 1 | 1 | 1 | — | Saved assignment page (/mod/assign/view.php) | — |

**Verified finish:** Saved assignment opened with the new name as its heading. [Browser capture](flow-03-created.png).

**Totals:** 8 clicks, 9 steps, 3 document loads, 1 fields.

## FLOW-04

**Role and start:** `teacher1` (editing teacher), Dashboard.

**Required fixture:** Use **Issue 16 grade a response** in `UX16-BASELINE`, with points out of 100, individual grading, and marking workflow disabled. The fixture has one submitted attempt from `student4` and no grade row. Verify the ungraded submission in the UI before proceeding. The seeded CS101 row for `student4` is a draft and is excluded.

**Finish:** Through the teacher's course and assignment UI, save 75 for `student4` and verify the saved grade and graded status.

| Step | Visible action | Click + | Step + | Document + | Field ID | Resulting state | Observation |
| ---: | --- | ---: | ---: | ---: | --- | --- | --- |
| 1 | Issue 16 Usability Baseline | 1 | 1 | 1 | — | Course (/course/view.php) | — |
| 2 | Issue 16 grade a response | 1 | 1 | 1 | — | Assignment grading summary (/mod/assign/view.php) | — |
| 3 | Submissions | 1 | 1 | 1 | — | Submission roster with ungraded student4 (/mod/assign/view.php) | — |
| 4 | Open Grade actions for the submitted student | 1 | 1 | 0 | — | Student row Grade menu | The grade actions sit in a row-level menu separate from submission status. |
| 5 | Grade in the row menu | 1 | 1 | 1 | — | Individual grading form, Not graded (/mod/assign/view.php) | — |
| 6 | Enter 75 in Grade out of 100 | 0 | 1 | 0 | grade | Grade entered | — |
| 7 | Save changes | 1 | 1 | 0 | — | Saved notice, Graded, gradebook 75.00 | — |

**Verified finish:** Moodle confirmed changes saved, showed Graded, and displayed 75.00 in the gradebook. [Browser capture](flow-04-graded.png).

**Totals:** 6 clicks, 7 steps, 4 document loads, 1 fields.

## FLOW-05

**Route variant:** URL-assisted. The chronological actions and counts below belong to the original assisted observation. The [human visible-navigation attempt](#manual-flow-05) was blocked. The successful total for that variant remains pending.

**Role and start:** `student2` (student), Dashboard.

**Required fixture:** `UX16-BASELINE` is visible, with student self-enrolment enabled, no enrolment key, no enrolment dates or seat limit, and no existing `student2` enrolment. Prepare enrolment settings before measurement. The seeded courses have self-enrolment disabled.

**Finish:** Find the course using the browser interface and self-enrol. Stop at the first visible sign of successful membership, such as access to the course as an enrolled student.

| Step | Visible action | Click + | Step + | Document + | Field ID | Resulting state | Observation |
| ---: | --- | ---: | ---: | ---: | --- | --- | --- |
| 1 | Navigate to the Moodle course catalogue in the address bar | 0 | 1 | 1 | — | Course catalogue (/course/index.php) | The site logo had returned to Dashboard in the preliminary attempt. |
| 2 | Enter UX16-BASELINE in Search courses | 0 | 1 | 0 | course-search | Course search query entered | — |
| 3 | Search courses | 1 | 1 | 1 | — | Search results (/course/search.php) | — |
| 4 | Issue 16 Usability Baseline | 1 | 1 | 1 | — | Self-enrolment options (/enrol/index.php) | — |
| 5 | Enrol me | 1 | 1 | 1 | — | Course page with confirmation covered by tour (/course/view.php) | — |
| 6 | Got it | 1 | 1 | 0 | — | Enrolment confirmation unobscured | The first-visit course tour covered the enrolment confirmation banner. |

**Verified finish:** Course page showed the unobscured confirmation You are enrolled in the course. [Browser capture](flow-05-enrolled.png).

**Totals:** 4 clicks, 6 steps, 4 document loads, 1 fields.

## Incomplete and exploratory attempts

On the initial Dashboard, a first-use overlay blocked the attempted course or logo clicks for FLOW-01, FLOW-02, and FLOW-05. They timed out before activation or navigation. The Dashboard tour was dismissed during setup before restarting. The teacher Dashboard locator also timed out during setup. Course-level tours encountered after a measured start are recorded in the successful runs.

Initial course-level target attempts for FLOW-02 (Grades), FLOW-03 (Edit mode), and FLOW-04 (assignment link) timed out before dispatch while the tour blocked the controls. The walkthrough then closed each tour using the recorded UI action. The initial FLOW-04 attempt was discarded after the locator chose the assignment link again instead of Grade. The retry started at Dashboard with the course tour already dismissed.

### FLOW-04 exploratory attempt

| Step | Visible action | Click + | Step + | Document + | Field ID | Resulting state | Observation |
| ---: | --- | ---: | ---: | ---: | --- | --- | --- |
| 1 | Issue 16 Usability Baseline | 1 | 1 | 1 | — | Course with tour (/course/view.php) | — |
| 2 | Skip tour | 1 | 1 | 0 | — | Course controls available | Teacher course tour overlaid assignment navigation. |
| 3 | Issue 16 grade a response | 1 | 1 | 1 | — | Assignment summary (/mod/assign/view.php) | — |
| 4 | Clicked the assignment link again while trying to select Grade | 1 | 1 | 1 | — | Same assignment summary. No grade action. (/mod/assign/view.php) | — |

### FLOW-05 exploratory attempt

| Step | Visible action | Click + | Step + | Document + | Field ID | Resulting state | Observation |
| ---: | --- | ---: | ---: | ---: | --- | --- | --- |
| 1 | figmoodle | 1 | 1 | 1 | — | Site logo returned to Dashboard. No course catalogue opened. (/my/) | — |

## Human-operated observations

These single-run results were supplied by the tester after the Playwright exploration. The assisted action rows, counts, and screenshots above remain separate. The tester supplied aggregate document counts and did not supply per-action network records, exact viewport sizes, or new screenshots. For the two narrow runs, the tester reported that the relevant click targets appeared unchanged. Viewport equivalence was not verified. The action notes below distinguish confirmed sequences from guide-based reconstruction.

### Manual FLOW-01

**Account and fixture:** `student5` in `UX16-MANUAL`. Narrow responsive viewport.

**Action record:** Six pointer activations and one online-text entry were confirmed. The guide sequence is course → assignment → **Add submission** → enter online text → **Save changes** → **Submit assignment** → **Continue**. The tester confirmed the aggregate, and an individual action trace was not supplied.

**Finish:** Submitted for grading. **Totals:** 6 clicks, 7 steps, 6 successful main-document loads, 1 field. Two HTTP 303 redirect responses were excluded. Per-action load attribution is unavailable.

### Manual FLOW-02

**Account and fixture:** `student1` in CS101. Narrow responsive viewport.

| Step | Tester-confirmed action | Click + | Step + | Field ID |
| ---: | --- | ---: | ---: | --- |
| 1 | Open **Introduction to Programming** from Dashboard | 1 | 1 | — |
| 2 | Select **Grades** | 1 | 1 | — |

**Finish:** **Assignment 1** grade 85/100. **Totals:** 2 clicks, 2 steps, 2 successful main-document loads, 0 fields. One HTTP 303 redirect response was excluded. Per-action load attribution is unavailable.

### Manual FLOW-03

**Account and fixture:** `teacher1` in `UX16-MANUAL`. Desktop layout.

**Confirmed interaction details:** Clicking the separate **Add** button, clicking to focus **Assignment name**, and entering the name. The guide records the intended route through the activity chooser. The tester did not supply the complete order or labels of all eight clicks, the tour state, or the action responsible for the fourth document load.

**Finish:** **Issue 16 manual assignment creation** was created successfully. **Totals:** 8 clicks, 9 steps, 4 successful main-document loads, 1 field. Per-action load attribution is unavailable.

**Sanitized access-log cross-check:** The local database associates the named assignment with course 6 and course module 9, created at 19:36:26 UTC on 22 November 2026. The matching access-log window contains these requests:

| UTC time | Request | HTTP status |
| --- | --- | ---: |
| 19:35:29 | GET course page for course 6 | 200 |
| 19:35:36 | GET course page for course 6 | 200 |
| 19:36:10 | GET assignment creation form for course 6 | 200 |
| 19:36:26 | POST assignment creation form | 303 |
| 19:36:26 | GET saved assignment for module 9 | 200 |

The four HTTP 200 page responses are consistent with the reported total. The 303 redirect adds no load under the counting rule. The access log has no browser-frame or user-action trace, so the second course-page request cannot be assigned to a click. The tester’s totals remain unchanged.

### Manual FLOW-04

**Account and fixture:** `teacher1` grading `student4` in `UX16-MANUAL`. Desktop layout.

| Step | Tester-confirmed action | Click + | Step + | Field ID |
| ---: | --- | ---: | ---: | --- |
| 1 | Open the course | 1 | 1 | — |
| 2 | Open the assignment | 1 | 1 | — |
| 3 | Select **Submissions** | 1 | 1 | — |
| 4 | Open the row’s three-dot **Grade actions** menu | 1 | 1 | — |
| 5 | Select **Grade** | 1 | 1 | — |
| 6 | Click the grade input to focus it | 1 | 1 | — |
| 7 | Enter `75` | 0 | 1 | grade |
| 8 | Select **Save changes** | 1 | 1 | — |

**Finish:** The gradebook showed 75.00. **Totals:** 7 clicks, 8 steps, 4 successful main-document loads, 1 field. Per-action load attribution is unavailable.

### Manual FLOW-05

**Account and start:** `student2`, Dashboard, seeking `UX16-MANUAL`. Viewport unreported.

| Step | Tester-confirmed action | Click + | Step + | Document + | Result |
| ---: | --- | ---: | ---: | --- | --- |
| 1 | Click the **figmoodle** site logo | 1 | 1 | Unrecorded | Returned to Dashboard |

**Status:** Blocked at course discovery. The learner found no visible catalogue route and did not enrol. Successful-workflow clicks, steps, document loads, and form fields remain pending. The table records only the unsuccessful attempt.
