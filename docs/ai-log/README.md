# AI usage log

## File organization

Copy `TEMPLATE.md` to `docs/ai-log/<github-username>.md` using your GitHub username as the filename. Keep the header and append one row per contributor-owned task. Update that row as outcomes and review results become known. Keep rows in start-date order and use ISO dates so logs can be filtered and combined across contributors without everyone editing the same file.

Use the issue and requirement ID from the contribution workflow. If an issue spans multiple contributors or PRs, identify each owned deliverable in `Task` and link its PR when available. Count each deliverable once. Group follow-up work on the same deliverable in its existing row.

## Fields

| Field | What to record |
| --- | --- |
| Date | Local calendar date when work started, in `YYYY-MM-DD` format. Keep this date when work continues across days. |
| Member | GitHub username of the contributor whose effort is recorded. |
| Module | Repository module affected by the deliverable. Use `docs` for documentation-only work outside a module. |
| Task | Issue number, requirement ID, and a short description identifying the owned deliverable. Add the PR reference when available. |
| AI used | `yes` if you consulted AI for the task, including when you discarded every output. Use `no` for manual work and `unknown` if use cannot be established. |
| Model/version | Model name and the version or identifier shown by the tool. Record the visible name and mark the version `unknown` if the tool hides it. |
| Context | Brief description of the inputs supplied to AI, such as file paths, requirements, relevant excerpts, and constraints. Omit secrets and sensitive content. |
| Outcome | One numbered status per substantive AI response, in response order. Use `accepted`, `modified`, `rejected`, `pending`, or `unknown` according to the rules below. Use `n/a` if there were no substantive responses and that is known. |
| Changes | What you kept, changed, or discarded from the AI output and why. Refer to response numbers when there are several. For manual work, briefly describe the change and its reason. |
| Time (h) | Total active contributor effort in decimal hours, using the timing rules below. |
| Review defects | Count of distinct defects found by teammate review. Use `0` when review is complete and found none, `pending` while review is absent or incomplete, and `unknown` if a completed review's count cannot be reconstructed. |

Use `n/a` for model, context, and outcome on manual tasks. When model or context varies across AI responses, identify which response numbers each entry applies to. Use `unknown` for unavailable values and update them when evidence becomes available. Escape any literal pipe in a cell as `\|`.

## Measurement rules

### Task time

Track active work in minutes for both AI-assisted and manual tasks. Include investigation, preparing context, prompting, inspecting AI output, self-review, editing, hands-on testing, and work responding to review feedback. Count time spent actively monitoring a test or AI run. Exclude passive waiting for AI, tests, or reviews, breaks, and time spent by the teammate reviewing your work. When switching tasks, stop timing the first one. Assign each minute once to the task receiving your attention.

Sum active minutes across sessions, then divide by 60 and round once to two decimal places for the row. Include time spent on discarded approaches. While work is underway, keep the total up to date. Use `unknown` if any required portion of the total cannot be reconstructed.

### AI outcomes and acceptance rate

Count each substantive response once. A response is substantive if it supplies code, tests, prose, analysis, or a proposed approach that could contribute to the task. A response with several suggestions still counts once. A follow-up response that revises or replaces the proposal counts separately. Exclude acknowledgments, tool errors, and responses that only ask for clarification.

- `accepted` means you used the substantive output in full. Formatting-only changes, such as whitespace or line wrapping, keep this classification.
- `modified` means you used some of the substantive output or made changes affecting its meaning or behavior. If you use only part of a response, record `modified` even if that part is unchanged.
- `rejected` means you decided to use none of the substantive output.
- `pending` means you have yet to decide how to use the response. Use `unknown` when the disposition cannot be reconstructed.

Record the disposition as of the latest version of the task and update it if your use of the output changes. AI outcome records the contributor's use of the response. Teammate review is tracked in `Review defects` and remains required before merging AI-generated code.

**AI acceptance rate** = `accepted / (accepted + modified + rejected) × 100%`, using counts of response statuses across the selected `AI used = yes` rows. Report the numerator and denominator with the percentage. Exclude `n/a`, `pending`, and `unknown` outcomes. Report how many responses were pending or unknown, and how many task rows had unknown AI use or an unavailable response history. If the denominator is zero, report `n/a`.

### Review defects

Count a defect when teammate review identifies a specific failure introduced by the task against a requirement, expected behavior, or documented project rule. Exclude preferences, questions, pre-existing problems unaffected by the task, and issues found only in self-review. Count a distinct underlying problem once across comments, reviewers, and review rounds, even if it affects several locations or is fixed before approval. Use the PR discussion to reconcile duplicates. If a defect spans multiple rows, assign it once to the deliverable that introduced it. Record the total when the PR is approved or the teammate explicitly concludes the review. If review reopens, use `pending` and record the cumulative total when it concludes again.

### Comparing AI-assisted and manual work

Use completed task rows with `AI used` set to `yes` or `no`, including tasks whose AI output was all rejected. Confirm completion from the linked PR merge or an explicit completion record in the issue. Apply the same start-date filter and compare tasks with similar module, scope, and type of work. Within each group, divide total logged hours by the number of tasks with known time to get mean hours per task. For mean review defects, divide total defects by the number of tasks with a numeric review count, including zeros. Exclude `pending` and `unknown` from the defect calculation and `unknown` time from the effort calculation. Report the task count and exclusions for each metric. Use `n/a` when there are no eligible tasks. These figures describe the logged work and can reflect differences in task scope or review coverage.

## Pull request summary

Fill in the PR's **AI usage** section and keep the detailed measurement in the contributor log. Where a PR covers several rows, summarize the rows for that PR.

| PR field | Log field and summary rule |
| --- | --- |
| AI used | `AI used`. Select Yes if any contributing row used AI and No if all were manual. If usage is unconfirmed, state what is unknown. |
| Model and version | `Model/version`. List the models and versions used. |
| Context given (files, requirement) | `Context`. Summarize the key files, requirements, and constraints supplied. |
| Outcome | `Outcome`. Use the matching status for a single decided response. For mixed outcomes, list the applicable statuses and their counts, and note any pending or unknown dispositions. |
| What was changed and why | `Changes`. Summarize the material changes and reasons. |
| Time spent (hours) | `Time (h)`. Report the sum of known active hours for the PR's rows and label the sum as partial if any row has unknown time. |

For manual work, use No, mark the AI-specific PR fields `n/a`, and report active task time. Date, member, module, task identifiers, response-level detail, and review defect counts stay in the detailed log for measurement and aggregation.

## Fictional example

The entry below is illustrative data. All identifiers and measurements are fictional.

| Date | Member | Module | Task | AI used | Model/version | Context | Outcome | Changes | Time (h) | Review defects |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-10-15 | example-contributor | advising | #9001 / REQ-ADV-99 / PR #9002 — Test prerequisite eligibility | yes | GPT-4.1 (gpt-4.1-2025-04-14) | Prerequisite requirement and existing eligibility test fixture | 1. modified | Kept the proposed test cases and changed the assertions to check the eligibility decision. | 1.25 | 0 |
