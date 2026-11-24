# AI log: performance KPI baseline (#15)

| Date | Member | Module | Task | AI used | Model/version | Context | Outcome | Changes | Time (h) | Review defects |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-11-22 | efekanegeli2 | docs | #15 / PERF-01..06 — k6 script `moodle-perf.js`, `summarize.py`, 3 baseline + 3 load runs, `docs/kpi/performance.md` | yes | Claude Opus 5.5 (claude-opus-5-5, Claude Code) | Issue #15, `docs/kpi/maintainability.md` (format), Docker Moodle and sample data from #8 | modified | After the first trial run, a content check was added to each request (expected text on the page), so a wrong page or a redirect to login cannot count as a fast response. All numbers come from the actual runs; targets are left as proposals for the team. Time estimated. | 1 | pending |
