# AI usage log

## File organization

Create one file per task (one PR) named `docs/ai-log/YYYY-MM-DD-<short-task-name>.md`, for example `2026-11-22-grading-rules.md`. Use the date the work started. Copy the header from `TEMPLATE.md` and add one row for the task. If several people work on the same PR, each adds their own row to the same file. Update the row when the outcome, time or review result changes.

## Fields

| Field | What to record |
| --- | --- |
| Date | Date the work started, `YYYY-MM-DD`. |
| Member | Your GitHub username. |
| Module | Module affected (`identity`, `course`, `advising`, `assignment`, `grading`, `files`, `planning`, `golden-master`, `infra`, `backend`). Use `docs` for documentation outside a module. |
| Task | Issue number, requirement IDs and a short description. Add the PR number when you have it. |
| AI used | `yes` or `no`. `yes` also when you discarded everything the AI gave you. |
| Model/version | Model name and version as the tool shows it. Write `version unknown` if the tool hides it. `n/a` for manual work. |
| Context | What you gave the AI: issue, files, requirements, constraints. No secrets. `n/a` for manual work. |
| Outcome | One value for the whole task: `accepted`, `modified`, `rejected` or `pending` (see below). `n/a` for manual work. |
| Changes | What you kept, changed or threw away from the AI output, and why. For manual work, what you changed. |
| Time (h) | Active working time in hours with up to two decimals, for example `0.5` or `1.25`. |
| Review defects | Number of defects found in teammate review. `pending` until the review is done, `0` if the review found none. |

## Measurement rules

### Time

Count the time you actively worked on the task: reading, preparing context, prompting, checking AI output, editing, testing and fixing review comments. Do not count waiting for the AI, a test run or a review, breaks, or the reviewer's time. Write the time down when you open the PR; estimate to the nearest quarter hour if you did not track it, and say `estimated` in Changes.

### Outcome

Judge the AI output for the task as a whole, at the time the PR is ready for review:

- `accepted`: you used the AI output as it was (formatting changes do not count).
- `modified`: you used part of it, or changed what it means or does.
- `rejected`: you used none of it.
- `pending`: you have not decided yet. Replace it before the PR is merged.

**AI acceptance rate** = `accepted / (accepted + modified + rejected) × 100%` over the `AI used = yes` rows of the period. Report the counts with the percentage, and say how many rows were still `pending`.

### Review defects

A defect is a specific problem a teammate found in review that the task introduced: wrong behavior, a broken requirement, or a broken project rule. Preferences, questions and problems that existed before the task do not count. Count the same problem once, even if it appears in several places or several reviewers mention it. Fill in the number when the PR is approved.

### Comparing AI-assisted and manual work

Use merged tasks only. Group rows with similar module and type of work, then compare per group: mean `Time (h)` per task and mean `Review defects` per task, for `AI used = yes` against `AI used = no`. Leave out rows with `pending` values and report how many rows each figure is based on.

## Pull request summary

Fill in the **AI usage** section of the PR from your row:

| PR field | Log field |
| --- | --- |
| AI used | AI used |
| Model and version | Model/version |
| Context given (files, requirement) | Context |
| Outcome | Outcome |
| What was changed and why | Changes |
| Time spent (hours) | Time (h) |

Date, member, module and review defects stay in the log file only.

## Example

The entry below is a made-up example.

| Date | Member | Module | Task | AI used | Model/version | Context | Outcome | Changes | Time (h) | Review defects |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-10-15 | example-contributor | advising | #9001 / REQ-ADV-99 / PR #9002 — Test prerequisite eligibility | yes | GPT-4.1 (gpt-4.1-2025-04-14) | Prerequisite requirement and existing eligibility test fixture | modified | Kept the proposed test cases and changed the assertions to check the eligibility decision. | 1.25 | 0 |

Log files written before these rules (without the `AI used` and `Review defects` columns) stay as they are.
