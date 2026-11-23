# AI log: grading rules and MDL-85503 (#14)

| Date | Member | Module | Task | Model/version | Context | Outcome (accepted/modified/rejected) | Changes | Time (h) |
|---|---|---|---|---|---|---|---|---|
| 2026-11-22 | ogithup | grading | #14 REQ-GRD requirements, data model, golden master scenarios, MDL-85503 reproduction and one slide | Claude Sonnet 5.5 (Claude Code) | Issue #14, Moodle 5.3.1 source (`v5.3.1`, `lib/grade/`, `grade/report/user/`, `course/`), Jira ticket MDL-85503 (REST API), running Docker Moodle | pending (review by teammate not done yet) | AI read the source, wrote `grading.md`, `verify_rules.php`, `reproduce.py` and the bug report. Every number in the docs comes from running the scripts against the Docker Moodle (`verify_results.txt`, `results.txt`). Found that MDL-85503 is a course category bug, not a grading bug, and documented it as such. | 0.5 |
