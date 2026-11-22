# AI log: maintainability baseline (#18, PR #22)

| Date | Member | Module | Task | Model/version | Context | Outcome (accepted/modified/rejected) | Changes | Time (h) |
|---|---|---|---|---|---|---|---|---|
| 2026-11-20 | ogithup | all (KPI) | Write `measure.py` and draft `docs/kpi/maintainability.md` | Claude Sonnet 5.5 (Claude Code) | Issue #18, README, CONTRIBUTING, Moodle 5.3.1 source | modified | Numbers come from running the script; folder mapping and targets left for team review | 1.5 |
| 2026-11-22 | sertugser | all (KPI) | Find why the PR #22 line counts differ from the team's cloc table, then align the script | Claude (Claude Code) | PR #22, team cloc table and script, Moodle 5.3.1 source (`1156a935`) | modified | Folder list aligned with the cloc table plus `user/` (pygount version missed `lib/grade`, `lib/completionlib.php`, `lib/enrollib.php`, context classes and most auth/enrol plugins); line counts switched from pygount to cloc (pygount skips bracket-only lines, about 20-26% lower); third-party code from `thirdpartylibs.xml` (e.g. `fpdi`) excluded; PHP and all-language columns reported separately; results and KPI ranges re-run | 0.5 |
