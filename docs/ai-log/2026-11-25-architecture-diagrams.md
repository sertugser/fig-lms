# AI log: architecture diagrams, old vs new, and live demo script

| Date | Member | Module | Task | AI used | Model/version | Context | Outcome | Changes | Time (h) | Review defects |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-11-25 | sertugser | docs | Week 2 presentation: Mermaid diagrams of the Moodle 5.3.1 architecture and of Fig LMS, side-by-side comparison, Moodle coupling count script, offline diagram page and live demo steps (`docs/architecture/`) | yes | Claude Opus 5.5 | Teacher feedback on the Nov 25 presentation, Moodle 5.3.1 source (`v5.3.1`), README, ADR-0001, `backend/`, `tests/golden-master/`, `docs/srs/`, `infra/` | pending | Every Moodle claim checked against the 5.3.1 source with file and line; backend answers, golden master table and the module-boundary failures were produced by running the code. Moodle UI steps of the demo not run (marked ⚠️). Time estimated. | 1.5 | pending |
