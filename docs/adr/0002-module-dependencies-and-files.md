---
status: proposed
date: 2026-11-27
decision-makers: project team
---

# ADR-0002: Module dependencies, inverted calls and a separate files module

## Context and Problem Statement

ADR-0001 chose a modular monolith whose module boundaries are checked by Spring Modulith (`ModularityTest`): a module may only use another module's `api` package, and modules may not depend on each other in a cycle. When the six Moodle sub-functions were implemented together, three questions came up:

1. Course enrolment must refuse a student who has not completed the prerequisites (REQ-ADV-09), but advising already needs course data, so a direct call from course to advising would create a cycle.
2. Assignment submissions store files, and file download must ask the owner of a file whether the caller may read it. A direct call in both directions would again create a cycle.
3. ADR-0001 lists file handling as one of the six sub-functions, but the module list (`identity`, `course`, `advising`, `assignment`, `grading`, `planning`) had no place for it.

How should modules depend on each other, and where do file handling and the shared building blocks live?

## Decision Drivers

* `ModularityTest` must keep passing: no cycles, no access to `internal` packages.
* Each Moodle rule should live in one module, so one owner reviews it.
* The design should stay close to how Moodle solves the same problem, so the golden master comparison stays easy to explain.

## Considered Options

* One-way dependencies with service provider interfaces (SPIs) for the calls that go "back", and a separate `files` module
* Spring application events for every call that goes back
* Merging course and advising, and putting file handling inside assignment

## Decision Outcome

Chosen option: "One-way dependencies with SPIs and a separate `files` module", because it keeps every rule in the module that owns it, it fails at compile time when a contract changes, and it mirrors Moodle's own plugin callbacks.

The dependency graph (an arrow means "uses the `api` of"):

```
identity  <- course <- grading <- advising <- assignment
   ^           ^         ^          ^           |
   |           +---------+----------+-----------+
   +--- files <------------------------------- -+
planning (empty so far)
```

* `identity` depends on nothing. `course` uses `identity`. `grading` uses `course` and `identity`. `advising` uses `course`, `grading` and `identity`. `files` uses `identity`. `assignment` uses all of them.
* **Inverted calls (SPIs).** The module that must stay independent publishes an interface in its `api` package; the other module implements it, and Spring injects every implementation:
  * `course.api.EnrolmentRule` — run before every enrolment. `advising.internal.PrerequisiteEnrolmentRule` implements it and refuses students with missing prerequisites (409 `PREREQUISITES_MISSING`). Moodle has the same hook idea in enrolment plugins and events, but no prerequisite rule (MDL-22648).
  * `files.api.FileAccessPolicy` — asked on every download, per component. `assignment.internal.SubmissionFilePolicy` implements it with the rule of Moodle's `assignsubmission_file_pluginfile()`. A component without a policy has no readable files (fail closed).
* **Activities push, the gradebook and completion never pull.** `assignment` sends grades through `grading.api.GradebookService.pushGrade()` (Moodle `grade_update()`) and completion states through `advising.api.CompletionService` (Moodle `completion_info::update_state()`).
* **Shared root package.** Only cross-cutting pieces are in `com.figlms`: `ApiException` and `ApiErrorHandler` (one error body `{"error": CODE, "message": text}` for every module, including 401 and 403), `Sql` (timestamp conversions) and the `Clock` bean, which tests replace to check date rules at exact moments.
* **Files module.** `com.figlms.files` stores contents by SHA-256 hash, cleans file names, computes the upload limit and serves downloads (REQ-FIL-01..07, `docs/srs/modules/files.md`).

### Consequences

* Good, because `ModularityTest` passes with seven modules and no cycle; adding a cycle by mistake still fails the build.
* Good, because each rule has one home: the prerequisite rule is in advising even though course runs it, and the submission download rule is in assignment even though files serves it.
* Good, because the SPIs are plain Java interfaces: a changed signature is a compile error, not a runtime surprise as with events.
* Bad, because a reader has to know that some behaviour of `course` and `files` comes from other modules. The SPI interfaces document their implementers to soften this.
* Bad, because `assignment` depends on five modules, so it is the most sensitive to changes in their APIs.
* Neutral, because `planning` is still empty; it will use `course`, `advising` and `identity`.

### Confirmation

* `ModularityTest.verifiesModuleBoundaries()` and `hasTheModulesFromAdr0001AndAdr0002()` run in CI.
* `CourseEnrolmentTest.invalid_prerequisiteRuleRefusesStudentEnrolment` checks the enrolment SPI; `AssignmentFlowTest.normal_onlyTheOwnerAndGradersDownloadASubmissionFile` checks the file SPI.

## Pros and Cons of the Options

### One-way dependencies with SPIs and a separate files module

* Good, because cycles are impossible by construction and the compiler checks the contracts.
* Bad, because the direction of each dependency has to be designed up front.

### Spring application events for the calls that go back

* Good, because the publishing module knows nothing about the listeners.
* Bad, because an enrolment or a download must wait for an answer ("may this happen?"), which events do not give; a refused enrolment would need extra plumbing.

### Merge course and advising; put files inside assignment

* Good, because no inversion is needed.
* Bad, because the modules no longer match the SRS modules and their owners, and every later module that stores files (for example a course image) would depend on assignment.
