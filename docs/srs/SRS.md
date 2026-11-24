# Software Requirements Specification: Fig LMS

| Version | Date | Owner | Status |
|---|---|---|---|
| 0.1 | 2026-11-24 | @sertugser | Skeleton for the first presentation (Nov 25) |

Structure based on ISO/IEC/IEEE 29148:2018 (software requirements specification). This file stays short: the requirements of each module are in their own file under [`modules/`](modules/), written by the module owner, so nobody edits someone else's file.

## 1. Introduction

### 1.1 Purpose
This document lists what Fig LMS must do so that it can replace selected Moodle subsystems without changing their behaviour, except where we fix a documented Moodle bug or add a new feature. It is the reference for issues, code, tests and the AI usage log: every piece of work names the requirement IDs it implements.

### 1.2 Scope
Fig LMS is a modular monolith (Java 21, Spring Boot 3, Spring Modulith, PostgreSQL, React; see [ADR-0001](../adr/0001-language-and-architecture.md)) that rebuilds these parts of Moodle 5.3.1:

| Module | Code | Flows in scope | Requirements |
|---|---|---|---|
| Identity & access | `IDN` | Login, three roles (student, teacher, admin), course-level permissions | [identity.md](modules/identity.md) |
| Courses & enrolment | `CRS` | Course creation, categories, teacher assignment, enrolment | [course.md](modules/course.md) |
| Advising | `ADV` | Course prerequisite check (MDL-22648), advisor approval, credit limit | [advising.md](modules/advising.md) |
| Assignments | `ASG` | Dates, file submission, late submission, drafts and attempts | [assignment.md](modules/assignment.md) |
| Grading | `GRD` | Grading, weighted mean, students viewing their own grades | [grading.md](modules/grading.md) |
| Study planner (new) | `PLN` | Term-by-term study plan from prerequisites and credit limits | [planning.md](modules/planning.md) |
| Non-functional | `NFR` | Maintainability, security, performance, usability | [kpi.md](kpi.md) |

Moodle bugs fixed in the new system: MDL-87490 (assignments), MDL-85503 (course categories). Out of scope: everything else in Moodle (forums, quizzes, messaging, plugins outside the flows above).

### 1.3 Definitions
| Term | Meaning |
|---|---|
| Preserved rule | Moodle behaviour the new system must reproduce exactly. Checked by a golden master test. |
| Bug fix | Moodle behaviour the new system deliberately changes, with a link to the Moodle tracker issue. Checked by a test that fails on Moodle's behaviour. |
| New | Behaviour Moodle does not have. Checked by acceptance tests. |
| Golden master test | The same input is given to Moodle (Docker, `infra/`) and to the new system, and the results must match ([tests/golden-master](../../tests/golden-master/README.md)). |
| Baseline | A KPI value measured on Moodle 5.3.1 with the sample data, to compare the new system against. |
| Moodle source | A path relative to Moodle's `public/` folder at tag `v5.3.1`, plus the function or class. |

### 1.4 References
- ISO/IEC/IEEE 29148:2018, Systems and software engineering — Life cycle processes — Requirements engineering
- Moodle 5.3.1 source, tag `v5.3.1`: https://github.com/moodle/moodle/tree/v5.3.1
- [ADR-0001](../adr/0001-language-and-architecture.md), [CONTRIBUTING.md](../../CONTRIBUTING.md), [AI usage log rules](../ai-log/README.md)

## 2. Overall description

### 2.1 Product perspective
Fig LMS runs next to the existing Moodle during the project. Moodle stays the reference: every preserved rule is first observed in the Docker Moodle with the shared sample data (`infra/sample-data`), then rebuilt. Each module is a Spring Modulith application module and talks to other modules only through their `api` package; `ModularityTest` fails the build otherwise.

### 2.2 User classes
| Role | Moodle role | Main tasks |
|---|---|---|
| Student | `student` | Enrol, submit assignments, see own grades, see own study plan |
| Teacher | `editingteacher` | Manage own courses, create and grade assignments, enrol students |
| Admin | `manager` | Manage categories, courses, users and roles |

### 2.3 Constraints
- Same input → same output as Moodle for preserved rules (golden master).
- All AI-assisted work is logged in `docs/ai-log/` and reviewed by a teammate before merge.
- No secrets in the repository.

## 3. Requirements

### 3.1 Requirement ID convention
Format: **`REQ-<MODULE>-<NN>`**, for example `REQ-ADV-01`.

| Part | Rule |
|---|---|
| `<MODULE>` | One of `IDN` identity, `CRS` courses, `ADV` advising, `ASG` assignments, `GRD` grading, `PLN` planner, `NFR` non-functional |
| `<NN>` | Two digits, numbered per module from `01`. A number is never reused, also when a requirement is dropped (mark it *withdrawn* instead). |

Use the ID in the issue title or body, in the PR (`Requirement ID:`), in the test name or comment, and in the AI log row. The [traceability matrix](traceability.md) is built from these.

### 3.2 Requirement row
Each module file has one table with these columns:

| Column | Content |
|---|---|
| ID | `REQ-<MODULE>-<NN>` |
| Type | `preserved`, `bug fix` or `new` |
| Description | What the system must do, in one paragraph. Bold short name first. |
| Moodle source | File and function in Moodle 5.3.1 that implement the rule (for new requirements: the gap it fills) |
| Acceptance criteria | Numbered, observable results. Preserved rules also list golden master scenarios: normal, boundary, invalid input, unauthorized access. |
| Owner | GitHub username of the module owner |

Complete example to copy from: `REQ-ADV-01` in [advising.md](modules/advising.md):

| ID | Type | Description | Moodle source | Acceptance criteria | Owner |
|---|---|---|---|---|---|
| REQ-ADV-01 | preserved | **Restriction tree.** An item without restrictions is available. Conditions are combined in a tree with one of four operators: AND (`&`), OR (`\|`), NOT AND (`!&`), NOT OR (`!\|`). AND needs every child, OR needs one child, and the NOT forms invert the children. Trees can be nested. An unknown operator is rejected. | `availability/classes/tree.php` `__construct()`, `check_available()`, `get_logic_flags()`; `availability/classes/info.php` `is_available()` | 1. No restriction → available. 2. AND(activity complete, from yesterday): student1 yes, student3 no. 3. OR(grade ≥ 80 %, activity incomplete): student1 (85) yes, student2 (72, passed) no, student3 (40, failed) yes. 4. NOT(activity complete): student1 no, student4 yes. 5. Operator `xor` → `coding_exception`. | @sertugser |

### 3.3 Functional requirements
| Module | File | Requirements (status 2026-11-24) |
|---|---|---|
| Identity & access | [identity.md](modules/identity.md) | `REQ-IDN-01`…`07`, 7 preserved |
| Courses & enrolment | [course.md](modules/course.md) | `REQ-CRS-01`…`09`, 8 preserved, 1 new |
| Advising | [advising.md](modules/advising.md) | `REQ-ADV-01`…`09`, 8 preserved, 1 new |
| Assignments | [assignment.md](modules/assignment.md) | `REQ-ASG-01`…`08`, 7 preserved, 1 bug fix (MDL-87490) |
| Grading | [grading.md](modules/grading.md) | `REQ-GRD-01`…`08`, 8 preserved |
| Study planner | [planning.md](modules/planning.md) | not written yet |

### 3.4 Non-functional requirements
Non-functional requirements use the `REQ-NFR-<NN>` IDs below. Each one points to its KPIs in [kpi.md](kpi.md), where the Moodle baseline, the target and the measurement method are listed.

| ID | Category | Requirement | KPIs |
|---|---|---|---|
| REQ-NFR-01 | Maintainability | The new code is less complex and better tested than the Moodle code it replaces, measured the same way. | MAINT-01…05 |
| REQ-NFR-02 | Security | Access control, authentication and configuration meet the targets of the OWASP-aligned checks. | SEC-01…11 |
| REQ-NFR-03 | Performance | The main flows respond at least as fast as Moodle under the same load. | PERF-01…06 |
| REQ-NFR-04 | Usability | The main flows need no more clicks, steps, page loads and form fields than the targets. | USAB click, step, page load and form field counts |
| REQ-NFR-05 | Modularity | Modules use each other only through their `api` package; CI fails otherwise (`ModularityTest`). | MAINT-02 (CI rule) |

## 4. Verification
| Type | How it is verified | Where |
|---|---|---|
| Preserved | Golden master test against the Docker Moodle with the sample data, plus the module's `verify_rules.php` | `tests/golden-master/`, `docs/srs/modules/<module>/` |
| Bug fix | A test that reproduces the Moodle bug and passes only with the fix | `backend/src/test/`, `docs/bugs/` |
| New | Acceptance criteria written as tests | `backend/src/test/` |
| Non-functional | KPI measured on Moodle (baseline) and on the new system with the same script | `docs/kpi/`, [kpi.md](kpi.md) |

Every requirement is listed in the [traceability matrix](traceability.md) with its issue, code and test. A requirement is *done* when all four columns are filled and CI is green.
