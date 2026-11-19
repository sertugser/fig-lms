# First day guide

Read this once before you start your first issue. It takes about 10 minutes.

## 1. Get access
- Accept the repository invitation (check your GitHub notifications or email).
- Open the project board: https://github.com/users/sertugser/projects/3
- Clone the repository:
  ```bash
  git clone https://github.com/sertugser/fig-lms.git
  cd fig-lms
  ```
- Read [README.md](../README.md) and [CONTRIBUTING.md](../CONTRIBUTING.md).

## 2. Find your work
- All week 1 issues are in the milestone **Week 1 – First presentation (Nov 25)**.
- Filter by your name: Issues → `assignee:<your-username>`.
- Some issues are temporarily assigned to @sertugser. The real owner is written in the **Owner** row at the top of the issue.
- Every issue tells you: the goal, the steps, where the output goes, and when it counts as done.

| Label | Meaning |
|---|---|
| `module: …` | Which module the work belongs to |
| `preserved-function` | A Moodle business rule we must keep |
| `bug` | A Moodle bug we reproduce and fix |
| `kpi` | A measurement on Moodle (baseline) |

## 3. Work on an issue
1. Move the card to **In Progress** on the board.
2. Create a branch: `<type>/<module>-<short-description>`, for example `docs/grading-business-rules`.
3. Commit with Conventional Commits: `docs(grading): add weighted mean rules`.
4. Open a pull request, fill in the template and write `Closes #<issue>`.
5. Ask one teammate for review. You cannot approve your own PR.
6. After merge the card moves to **Done**.

## 4. Reading Moodle code
Most week 1 issues ask you to read Moodle source. Get the exact version we analyse:
```bash
git clone --depth 1 -b MOODLE_503_STABLE https://github.com/moodle/moodle.git
```
Since Moodle 5.1 the code lives under `public/`. Paths in the issues are relative to that folder.

To try things in a running Moodle, use the Docker setup from #8 once it is merged.

## 5. Writing requirements
- Write your rules in your module's section of `docs/srs/SRS.md` (created in #3).
- Requirement ID format: `REQ-<MODULE>-<NN>`, for example `REQ-ASG-04`.
- Each requirement points to the Moodle file and function it comes from.

## 6. AI usage
If you use an AI tool, fill in the AI section of the PR template and add an entry to `docs/ai-log/`. See #11 for the final template.

## 7. Key dates
| Date | What |
|---|---|
| Sat, Nov 21 | Moodle in Docker (#8), backend skeleton (#5), AI log template (#11) |
| Sun, Nov 22 | SRS skeleton (#3), advising rules (#4) |
| Mon, Nov 23 | Business rules, bug reproductions, KPI baselines, golden master harness, literature review (#20) |
| Tue, Nov 24, 12:00 | Everyone adds one slide about their module |
| Tue, Nov 24, evening | Rehearsal |
| Wed, Nov 25 | First presentation |
