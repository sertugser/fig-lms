# Traceability matrix

Every requirement from [SRS.md](SRS.md) with the issue that defines it, the code that implements it and the test that verifies it. Update your rows in the same PR that adds the code or the test.

- **Issue:** the analysis issue that wrote the requirement, then the implementation issue (if any).
- **Code:** package or class in `backend/src/main/java/com/figlms/`.
- **Test:** test class (backend) or golden master test (`tests/golden-master/`). For preserved rules, the module's `verify_rules.php` shows the Moodle side.
- An empty cell means: not done yet.

Status on 2026-11-24: requirements written for five modules (41 requirements), first implementation `REQ-ADV-09` (#6).

## Identity & access

| Requirement | Type | Issue | Code | Test |
|---|---|---|---|---|
| REQ-IDN-01 | preserved | #7 | | |
| REQ-IDN-02 | preserved | #7 | | |
| REQ-IDN-03 | preserved | #7 | | |
| REQ-IDN-04 | preserved | #7 | | |
| REQ-IDN-05 | preserved | #7 | | |
| REQ-IDN-06 | preserved | #7 | | |
| REQ-IDN-07 | preserved | #7 | | |

## Courses & enrolment

| Requirement | Type | Issue | Code | Test |
|---|---|---|---|---|
| REQ-CRS-01 | preserved | #9 | | |
| REQ-CRS-02 | preserved | #9 | | |
| REQ-CRS-03 | preserved | #9 | | |
| REQ-CRS-04 | preserved | #9 | | |
| REQ-CRS-05 | preserved | #9 | | |
| REQ-CRS-06 | preserved | #9 | | |
| REQ-CRS-07 | preserved | #9 | | |
| REQ-CRS-08 | preserved | #9 | | |
| REQ-CRS-09 | new | #9 | | |

## Advising

| Requirement | Type | Issue | Code | Test |
|---|---|---|---|---|
| REQ-ADV-01 | preserved | #4 | | |
| REQ-ADV-02 | preserved | #4 | | |
| REQ-ADV-03 | preserved | #4 | | |
| REQ-ADV-04 | preserved | #4 | | |
| REQ-ADV-05 | preserved | #4 | | |
| REQ-ADV-06 | preserved | #4 | | |
| REQ-ADV-07 | preserved | #4 | | |
| REQ-ADV-08 | preserved | #4 | | |
| REQ-ADV-09 | new | #4, #6 | `advising.api.PrerequisiteService`, `advising.internal.PrerequisiteController` | `PrerequisiteServiceTest`, `PrerequisiteControllerTest`; golden master #13 |

## Assignments

| Requirement | Type | Issue | Code | Test |
|---|---|---|---|---|
| REQ-ASG-01 | preserved | #10 | | |
| REQ-ASG-02 | preserved | #10 | | |
| REQ-ASG-03 | preserved | #10 | | |
| REQ-ASG-04 | preserved | #10 | | |
| REQ-ASG-05 | preserved | #10 | | |
| REQ-ASG-06 | preserved | #10 | | |
| REQ-ASG-07 | preserved | #10 | | |
| REQ-ASG-08 | bug fix (MDL-87490) | #10 | | |

## Grading

| Requirement | Type | Issue | Code | Test |
|---|---|---|---|---|
| REQ-GRD-01 | preserved | #14 | | |
| REQ-GRD-02 | preserved | #14 | | |
| REQ-GRD-03 | preserved | #14 | | |
| REQ-GRD-04 | preserved | #14 | | |
| REQ-GRD-05 | preserved | #14 | | |
| REQ-GRD-06 | preserved | #14 | | |
| REQ-GRD-07 | preserved | #14 | | |
| REQ-GRD-08 | preserved | #14 | | |

## Study planner

| Requirement | Type | Issue | Code | Test |
|---|---|---|---|---|
| (none yet) | | | | |

## Non-functional

| Requirement | Type | Issue | Code | Test |
|---|---|---|---|---|
| REQ-NFR-01 Maintainability | NFR | #18, #26 | | `docs/kpi/maintainability/measure.py` on the new code |
| REQ-NFR-02 Security | NFR | #17 | | SEC checks of `docs/kpi/security.md` on the new system |
| REQ-NFR-03 Performance | NFR | #15 | | |
| REQ-NFR-04 Usability | NFR | #16 | | Playwright click counts (`tests/e2e/`) |
| REQ-NFR-05 Modularity | NFR | #5 | all modules | `ModularityTest` |
