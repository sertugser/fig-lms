# Contributing

## Workflow
1. Every piece of work starts with an **issue** that carries a requirement ID (e.g. `REQ-ADV-03`). The traceability matrix is built from these.
2. No direct pushes to `main`. Branch name: `<type>/<module>-<short-description>`
   - Examples: `feat/advising-prerequisite`, `fix/assignment-MDL-87490`, `test/grading-golden-master`
3. Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/): `<type>(<module>): <summary>` with type `feat`, `fix`, `test`, `docs`, `refactor` or `chore`.
   - Example: `fix(assignment): reverting to draft must not grant an extra attempt (MDL-87490)`
4. Open a pull request. **At least one teammate** reviews and approves it (the "expert checkpoint" in the project brief). Authors cannot approve their own PRs.
5. CI (build, tests, `ApplicationModules.verify()`) must be green before merging.
6. Use "Squash and merge"; link the issue with `Closes #12`.

## AI usage
For any AI-generated code, fill in the **AI section** of the PR template and add an entry to `docs/ai-log/`:
model and version, context given, accepted / modified / rejected, time spent. AI output is never merged without review.

## Tests
- At least one golden master test for each preserved business rule (`tests/golden-master/`).
- For bug fixes: first a failing test that reproduces the bug, then the fix.
- For new features: acceptance criteria are written as tests.

## Code rules
- Modules never access each other's internal classes; they talk only through their `api` package.
- No secrets (passwords, keys) in the repository; use `.env.example`.
