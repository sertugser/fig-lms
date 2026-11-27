# Traceability matrix

Every requirement from [SRS.md](SRS.md) with the issue that defines it, the code that implements it and the test that verifies it. Update your rows in the same PR that adds the code or the test.

- **Issue:** the analysis issue that wrote the requirement, then the implementation issue (if any).
- **Code:** package or class in `backend/src/main/java/com/figlms/`.
- **Test:** test class (backend) or golden master test (`tests/golden-master/`). For preserved rules, the module's `verify_rules.php` shows the Moodle side.
- An empty cell means: not done yet.

Status on 2026-11-27: requirements written for six modules (49 requirements); identity, course, advising, assignment, grading and files are implemented (core modules PR). The study planner is next.

## Identity & access

| Requirement | Type | Issue | Code | Test |
|---|---|---|---|---|
| REQ-IDN-01 | preserved | #7 | `identity.internal.DatabaseUserDetailsService`, `AccountController` | `IdentityTest` |
| REQ-IDN-02 | preserved | #7 | `identity.api.Role`, `course.internal.EnrolmentService` | `CourseEnrolmentTest` |
| REQ-IDN-03 | preserved | #7 | `course.api.AccessControl` (`DefaultAccessControl.requireCourseAccess`) | `CourseEnrolmentTest`, `AssignmentFlowTest` |
| REQ-IDN-04 | preserved | #7 | `identity.api.Capability`, `course.api.AccessControl` | `AssignmentFlowTest`, `GradebookTest`, `AvailabilityAndCompletionTest` |
| REQ-IDN-05 | preserved | #7 | `course.internal.EnrolmentService.enrolManually` | `CourseEnrolmentTest` |
| REQ-IDN-06 | preserved | #7 | `identity.internal.LoginThrottle` | `IdentityTest` |
| REQ-IDN-07 | preserved | #7 | `identity.internal.PasswordPolicy`, `SecurityConfig` (session timeout) | `IdentityTest` |

## Courses & enrolment

| Requirement | Type | Issue | Code | Test |
|---|---|---|---|---|
| REQ-CRS-01 | preserved | #9 | `course.internal.CourseService` | `CourseEnrolmentTest` |
| REQ-CRS-02 | preserved | #9 | `course.internal.CourseService.validateDates`, `defaults` | `CourseEnrolmentTest` |
| REQ-CRS-03 | preserved | #9 | `course.internal.CourseService.create` (default enrolment methods, creator enrolled) | `CourseEnrolmentTest` |
| REQ-CRS-04 | preserved | #9 | `course.internal.CategoryService` | `BrowseOwnCoursesTest` (listing; moving and deleting categories have no test yet) |
| REQ-CRS-05 | preserved | #9 | `course.internal.EnrolmentService.enrolManually` | `CourseEnrolmentTest` |
| REQ-CRS-06 | preserved | #9 | `course.internal.CourseRepository.EnrolmentRow.isActive`, `DefaultAccessControl` | `CourseEnrolmentTest` |
| REQ-CRS-07 | preserved | #9 | `course.internal.EnrolmentService` (`assignableRoles`), `identity.api.Role.assignableRoles` | `CourseEnrolmentTest` |
| REQ-CRS-08 | preserved | #9 | `course.internal.EnrolmentService.enrolSelf` | `CourseEnrolmentTest` |
| REQ-CRS-09 | new | #9 | V4 migration (unique constraints, period check), `EnrolmentService` | `CourseEnrolmentTest` |
| REQ-CRS-10 | bug fix (MDL-85503) | #9 | `course.internal.CategoryService.view` (`fig.course.browse-all-courses`) | `BrowseOwnCoursesTest` |

## Advising

| Requirement | Type | Issue | Code | Test |
|---|---|---|---|---|
| REQ-ADV-01 | preserved | #4 | `advising.internal.AvailabilityEvaluator` | `AvailabilityAndCompletionTest` |
| REQ-ADV-02 | preserved | #4 | `advising.internal.AvailabilityEvaluator`, `CompletionTracker` | `AvailabilityAndCompletionTest` |
| REQ-ADV-03 | preserved | #4 | `advising.internal.AvailabilityEvaluator` | `AvailabilityAndCompletionTest` |
| REQ-ADV-04 | preserved | #4 | `advising.internal.AvailabilityEvaluator` | `AvailabilityAndCompletionTest` |
| REQ-ADV-05 | preserved | #4 | `advising.internal.CompletionTracker`, `AdvisingController` | `AvailabilityAndCompletionTest`, `AssignmentFlowTest` |
| REQ-ADV-06 | preserved | #4 | `advising.internal.AvailabilityEvaluator` (information text, hiding), `assignment.internal.AssignmentService.list` | `AvailabilityAndCompletionTest`, `AssignmentFlowTest` |
| REQ-ADV-07 | preserved | #4 | `advising.internal.AvailabilityEvaluator` (`moodle/course:ignoreavailabilityrestrictions`) | `AvailabilityAndCompletionTest`, `AssignmentFlowTest` |
| REQ-ADV-08 | preserved | #4 | `advising.internal.CompletionTracker` (course criterion); the new enrolment check is `PrerequisiteEnrolmentRule` (REQ-ADV-09) | `AvailabilityAndCompletionTest`, `CourseEnrolmentTest` |
| REQ-ADV-09 | new | #4, #6 | `advising.api.PrerequisiteService`, `advising.internal.PrerequisiteController` | `PrerequisiteServiceTest`, `PrerequisiteControllerTest`, `AssignmentFlowTest`; golden master `PrerequisiteGoldenMasterTest` (#13) |

## Assignments

| Requirement | Type | Issue | Code | Test |
|---|---|---|---|---|
| REQ-ASG-01 | preserved | #10 | `assignment.internal.Window`, `AssignmentService` (date validation, overrides, extensions) | `WindowTest`, `AssignmentFlowTest` |
| REQ-ASG-02 | preserved | #10 | `assignment.internal.Window.isLate` | `WindowTest`, `AssignmentFlowTest` |
| REQ-ASG-03 | preserved | #10 | `assignment.internal.SubmissionService.editRefusal` | `AssignmentFlowTest` |
| REQ-ASG-04 | preserved | #10 | `assignment.internal.SubmissionService.save`, `submit` | `AssignmentFlowTest` |
| REQ-ASG-05 | preserved | #10 | `assignment.internal.SubmissionService.save`, `files.api.FileStore` | `AssignmentFlowTest` |
| REQ-ASG-06 | preserved | #10 | `assignment.internal.AssignmentGrading.addAttempt`, `grade` | `AssignmentFlowTest` |
| REQ-ASG-07 | preserved | #10 | `assignment.internal.AssignmentGrading.reopenIfRequired` | `AssignmentFlowTest` |
| REQ-ASG-08 | bug fix (MDL-87490) | #10 | `assignment.internal.AssignmentGrading.revertToDraft` | `AssignmentFlowTest.bugfix_revertToDraftKeepsTheSameAttemptAndItsFile` |

## Grading

| Requirement | Type | Issue | Code | Test |
|---|---|---|---|---|
| REQ-GRD-01 | preserved | #14 | `grading.internal.GradeCalculator`, `Aggregation` | `GradeCalculatorTest` |
| REQ-GRD-02 | preserved | #14 | `grading.internal.GradeCalculator` | `GradeCalculatorTest` |
| REQ-GRD-03 | preserved | #14 | `grading.internal.GradeCalculator` | `GradeCalculatorTest` |
| REQ-GRD-04 | preserved | #14 | `grading.internal.GradeCalculator`, `GradeReports.updateCategory` | `GradeCalculatorTest`, `GradebookTest` |
| REQ-GRD-05 | preserved | #14 | `grading.internal.Gradebook.bounded` | `GradebookTest` |
| REQ-GRD-06 | preserved | #14 | `grading.internal.GradeReports.userReport` | `GradebookTest` |
| REQ-GRD-07 | preserved | #14 | `grading.internal.GradeReports.userReport` | `GradebookTest` |
| REQ-GRD-08 | preserved | #14 | `grading.internal.GradeReports` | `GradebookTest` |

## Files

| Requirement | Type | Issue | Code | Test |
|---|---|---|---|---|
| REQ-FIL-01 | preserved | | `files.internal.ContentStore` | `FileStoreTest` |
| REQ-FIL-02 | preserved | | `files.internal.DefaultFileStore.store`, V7 unique constraint | `FileStoreTest` |
| REQ-FIL-03 | preserved | | `files.internal.DefaultFileStore.delete` | `FileStoreTest` |
| REQ-FIL-04 | preserved | | `files.api.FileNames.clean` | `FileNamesTest`, `AssignmentFlowTest` |
| REQ-FIL-05 | preserved | | `files.internal.FileController`, `files.api.FileAccessPolicy`, `assignment.internal.SubmissionFilePolicy` | `AssignmentFlowTest` |
| REQ-FIL-06 | preserved | | `files.internal.DefaultFileStore.maxUploadBytes` | `FileStoreTest`, `AssignmentFlowTest` |
| REQ-FIL-07 | new | | `files.internal.ContentStore` | `FileStoreTest` |

## Study planner

| Requirement | Type | Issue | Code | Test |
|---|---|---|---|---|
| (none yet) | | | | |

## Non-functional

| Requirement | Type | Issue | Code | Test |
|---|---|---|---|---|
| REQ-NFR-01 Maintainability | NFR | #18, #26 | | `docs/kpi/maintainability/measure.py` on the new code |
| REQ-NFR-02 Security | NFR | #17 | | SEC checks of `docs/kpi/security.md` on the new system |
| REQ-NFR-03 Performance | NFR | #15 | | k6 script `docs/kpi/performance/moodle-perf.js` on the new system |
| REQ-NFR-04 Usability | NFR | #16 | | Playwright click counts (`tests/e2e/`) |
| REQ-NFR-05 Modularity | NFR | #5 | all modules | `ModularityTest` |
