# Issue #16 human measurement guide

**Status:** Four human walkthroughs are recorded. FLOW-05 was blocked at course discovery. See the [human records](measurements.md#human-operated-observations). The five Playwright-assisted observations came first and remain separate. Use the blank tables below for repeat measurements.

## Before each run

1. In normal Chrome or another authorized browser, open the local Moodle **Dashboard** at `http://localhost:8000/my/`. Sign in with the named sample account using the [local setup instructions](../../../infra/README.md). Use separate browser profiles or private windows for different accounts. Use English, Boost, and a desktop viewport comparable to 1440 × 1000. Record browser, viewport, account, fixture, date, and run status.
2. Establish a returning-user state outside the measured run. On Dashboard, close any welcome tour via **Got it**, **Skip tour**, or **Next** through the final confirmation. Open a course as the same account, dismiss any course tour through the same visible controls, and revisit the relevant page to verify the tour stays closed. For students use CS101, which the sample accounts already belong to. For the teacher, open the course and an existing assignment in the already-used `UX16-BASELINE` course. If the grader has a tour, dismiss it using that already-graded fixture. Keep the fresh grading attempt in `UX16-MANUAL` untouched. For FLOW-05, warm up the student course tour in CS101 and keep the learner unenrolled in `UX16-MANUAL`.
3. Return to Dashboard using the visible **Dashboard** link. For FLOW-03 and FLOW-04, turn **Edit mode** off during setup, then start from Dashboard. Check that the correct account and anticipated course or role are present. If a tour appears during the measured route, record the run as a separate tour-present variant. Keep its actual clicks, and repeat from a verified returning-user state when possible.
4. Open browser Developer Tools in an undocked window to keep the viewport stable. In **Network**, enable **Preserve log** and select **Doc** or **Document**. Once Dashboard is ready, clear the log and start counting. Keep the next action separate until any resulting load has settled.

## Counting and recording

Use the [baseline counting rules](../usability.md#measurement-methodology). Record one row per purposeful action and its visible result. A pointer activation is one click and one step. Entering a field value is one logical entry step with no click for the typing. Count a separate pointer activation if used to focus or open a control. A keyboard shortcut that activates a control has one step and zero clicks. Record meaningful changes in input method.

Count the final committed main-frame document after a redirect chain once. Exclude dashboard setup, child frames, dynamic updates, and dialogs. Record the field identifier the first time its value changes with field increment 1. Use 0 for subsequent changes to the same field, navigation controls, buttons, and untouched defaults. Stop at the first unobscured completion state. Do not retry failed steps silently. Keep incomplete attempts and their observations separate from successful totals.

Add each completed table or incomplete attempt to the human section of [measurements.md](measurements.md#human-operated-observations), with a distinct run label, browser, tour state, fixture, status, and visible finish or blocker. Link local evidence only if it contains no secrets or session details. Sum the increment columns after checking the action log. Keep the published human figures pending until the logs have been validated.

## FLOW-01

**Start:** `student5`, student, on Dashboard. **Fixture:** In **Issue 16 Manual Measurement** (`UX16-MANUAL`), **Issue 16 submit a response** is individual, online-text only, and requires explicit final submission. No required statement, dates, time limit, prior attempt, or lock. Confirm **Add submission** is offered when you reach the assignment.

1. Select the course card on Dashboard and open **Issue 16 submit a response**.
2. Select **Add submission**, enter `Issue 16 sample submission.` in **Online text**, then select **Save changes**.
3. Verify the saved draft, select **Submit assignment**, then **Continue**. Stop when **Submitted for grading** and the submitted text are shown.

**Reset:** Create a fresh assignment with the same settings in the manual course and use a unique name. Leave the submitted attempt intact.

| Step | Visible action | Click + | Step + | Document + | Field ID | Field + | Result or evidence |
| --- | --- | --- | --- | --- | --- | --- | --- |
| | | | | | | | |

## FLOW-02

**Start:** `student1`, student, on Dashboard with the CS101 course tour already closed. **Fixture:** CS101 **Introduction to Programming → Assignment 1** has an unhidden grade of 85/100 for this learner.

1. Select **Introduction to Programming** on Dashboard.
2. Select **Grades** in the course tabs. Stop when the learner’s **User report** shows **Assignment 1** and **85.00** out of 100.

**Reset:** Return to Dashboard. Keep the existing grade unchanged.

| Step | Visible action | Click + | Step + | Document + | Field ID | Field + | Result or evidence |
| --- | --- | --- | --- | --- | --- | --- | --- |
| | | | | | | | |

## FLOW-03

**Start:** `teacher1`, editing teacher, on Dashboard with **Edit mode** off and the teacher course tour closed. **Fixture:** A section named **New section** exists in **Issue 16 Manual Measurement**. The new assignment name is unused.

1. Open the manual course and switch on **Edit mode**.
2. In **New section**, select **Add content → Activity or resource → Assignment → Add**, recording each distinct activation.
3. Enter `Issue 16 manual assignment creation` in **Assignment name**. Leave other settings at their defaults. Select **Save and display**. Stop when the named saved assignment opens.

**Reset:** Return to Dashboard with **Edit mode** off and use a uniquely suffixed assignment name for the next attempt. Keep the previous activity intact.

| Step | Visible action | Click + | Step + | Document + | Field ID | Field + | Result or evidence |
| --- | --- | --- | --- | --- | --- | --- | --- |
| | | | | | | | |

## FLOW-04

**Start:** `teacher1`, editing teacher, on Dashboard with **Edit mode** off and course and grader tours closed. **Fixture:** **Issue 16 grade a response** in the manual course has one submitted online-text attempt by `student4` with no grade. It is an individual points assignment out of 100, with marking workflow off.

1. Open the manual course and the grading assignment. Select **Submissions**.
2. Locate `student4` with **Submitted for grading** and an unset grade. Open the row’s **Grade actions** menu and select **Grade**. Verify the correct learner and **Not graded**.
3. Enter `75` in **Grade out of 100** and select **Save changes**. Stop when the page shows **Graded** and **Current grade in gradebook** shows **75.00**.

**Reset:** Create a fresh online-text assignment with the same grade settings, no dates or time limit, and **Require students to click the submit button** set to **No**. As `student4`, add and save `Issue 16 submission awaiting grading.` during setup. Verify **Submitted for grading** with no grade before restarting. Keep the previous grade intact.

| Step | Visible action | Click + | Step + | Document + | Field ID | Field + | Result or evidence |
| --- | --- | --- | --- | --- | --- | --- | --- |
| | | | | | | | |

## FLOW-05

**Start:** `student2`, student, on Dashboard with Dashboard and course tours closed through setup in CS101. **Fixture:** **Issue 16 Manual Measurement** (`UX16-MANUAL`) is visible and enables student self-enrolment without a key, dates, or a seat cap. The learner is not enrolled.

1. From Dashboard, look for a visible site-wide **Courses**, **All courses**, **Site home**, or catalogue search control. Follow it only if it is actually present. A search restricted to already-enrolled courses is insufficient. The previous site-logo attempt returned to Dashboard. If no visible route leads to a catalogue or course listing, record the controls observed, any actions taken, and **blocked at course discovery**. Leave the success totals pending. Do not use the address bar, bookmarks, direct links, or a newly added navigation block to finish this variant.
2. If a public listing or search is reachable, look for `UX16-MANUAL` or browse **Engineering**. Record the controls actually used. Open **Issue 16 Manual Measurement** from the listing and verify that **Enrol me** is offered with no key field.
3. Select **Enrol me**. Stop at the unobscured **You are enrolled in the course** confirmation. If a tour appears, retain that separate variant. The successful total remains pending unless a human completes enrolment through a visible route.

**Reset:** Use a fresh visible course with equivalent self-enrolment settings for a fresh run. Confirm the learner is unenrolled before starting. Keep the first enrolment intact.

| Step | Visible action | Click + | Step + | Document + | Field ID | Field + | Result or evidence |
| --- | --- | --- | --- | --- | --- | --- | --- |
| | | | | | | | |
