# AI log: coverage baseline MAINT-05 (#26)

| Date | Member | Module | Task | Model/version | Context | Outcome (accepted/modified/rejected) | Changes | Time (h) |
|---|---|---|---|---|---|---|---|---|
| 2026-11-22 | ogithup | all (KPI) | #26 Measure the Moodle test coverage baseline (PCOV image variant, PHPUnit set-up, run and summary scripts, report) | Claude Sonnet 5.5 (Claude Code) | Issue #26, `infra/` (Dockerfile, Compose, config), `docs/kpi/maintainability.md` and `measure.py`, running Docker Moodle 5.3.1 | pending (review by teammate not done yet) | AI wrote the Dockerfile variant, Compose file, `run_coverage.py`, `summarize_coverage.py` and the report text. All numbers come from running PHPUnit in the container; test counts were cross-checked against Moodle's own test suites (Assignments: 646 = 559 + 87). The target proposal (80%) is left for the team to decide. | 0.67 |
