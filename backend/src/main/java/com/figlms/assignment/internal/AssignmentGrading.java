package com.figlms.assignment.internal;

import com.figlms.ApiException;
import com.figlms.advising.api.CompletionService;
import com.figlms.advising.api.CompletionState;
import com.figlms.course.api.AccessControl;
import com.figlms.grading.api.GradebookService;
import com.figlms.identity.api.Capability;
import com.figlms.identity.api.Role;
import com.figlms.identity.api.UserAccount;
import com.figlms.identity.api.UserDirectory;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The teacher's side of an assignment (REQ-ASG-06..08): the grading table, grading, attempts and reverting to draft.
 * Grades of the latest attempt go to the gradebook ({@code grade_update()}) and to activity completion.
 */
@Service
class AssignmentGrading {

    record GradingRow(String username, String fullName, int attemptNumber, String status, Instant timeModified,
            boolean late, Double grade, boolean locked, Instant extensionDueDate) {
    }

    /**
     * @param attemptNumber attempt to grade; {@code null} = the latest
     * @param addAttempt    "Allow another attempt" in the grading form (counts only for {@link ReopenMethod#MANUAL})
     */
    record GradeInput(Double grade, Integer attemptNumber, Boolean addAttempt) {
    }

    record GradeResult(String username, int gradedAttempt, Double grade, int latestAttempt, boolean attemptAdded) {
    }

    private final AssignmentService assignments;
    private final AssignmentRepository repository;
    private final AccessControl access;
    private final UserDirectory users;
    private final GradebookService gradebook;
    private final CompletionService completion;
    private final Clock clock;

    AssignmentGrading(AssignmentService assignments, AssignmentRepository repository, AccessControl access,
            UserDirectory users, GradebookService gradebook, CompletionService completion, Clock clock) {
        this.assignments = assignments;
        this.repository = repository;
        this.access = access;
        this.users = users;
        this.gradebook = gradebook;
        this.completion = completion;
        this.clock = clock;
    }

    /** Every student of the course with the state of their latest attempt (Moodle grading table). */
    @Transactional(readOnly = true)
    List<GradingRow> table(String caller, long assignmentId) {
        AssignmentRepository.AssignmentRow row = assignments.require(assignmentId);
        access.require(caller, row.courseId(), Capability.ASSIGN_GRADE);
        List<GradingRow> rows = new ArrayList<>();
        for (long userId : access.usersWithRole(row.courseId(), Role.STUDENT, true)) {
            UserAccount user = users.findById(userId).orElseThrow();
            Window window = assignments.window(row, userId);
            Optional<AssignmentRepository.SubmissionRow> s = repository.latestSubmission(row.id(), userId);
            rows.add(new GradingRow(user.username(), user.fullName(),
                    s.map(AssignmentRepository.SubmissionRow::attemptNumber).orElse(0),
                    s.map(x -> x.status().name()).orElse(SubmissionStatus.NEW.name()),
                    s.map(AssignmentRepository.SubmissionRow::timeModified).orElse(null),
                    s.filter(x -> x.status() == SubmissionStatus.SUBMITTED).map(x -> window.isLate(x.timeModified()))
                            .orElse(false),
                    s.flatMap(x -> repository.grade(row.id(), userId, x.attemptNumber()))
                            .map(AssignmentRepository.GradeRow::grade).orElse(null),
                    window.locked(), window.extensionDueDate()));
        }
        return rows;
    }

    /**
     * Grades one attempt (Moodle {@code apply_grade_to_user()} and {@code update_grade()}). A student without a
     * submission gets attempt 0 created, as Moodle's grading form does (REQ-ASG-06). Only a grade of the latest
     * attempt reaches the gradebook and may open another attempt (REQ-ASG-07).
     */
    @Transactional
    GradeResult grade(String caller, long assignmentId, String username, GradeInput in) {
        AssignmentRepository.AssignmentRow row = assignments.require(assignmentId);
        access.require(caller, row.courseId(), Capability.ASSIGN_GRADE);
        UserAccount student = assignments.requireParticipant(row, username);
        UserAccount grader = users.require(caller);
        Double grade = in.grade();
        if (grade != null && grade < 0) {
            throw ApiException.invalid("GRADE_BELOW_ZERO", "Grade must be greater than or equal to zero.");
        }
        if (grade != null && grade > row.maxGrade()) {
            throw ApiException.invalid("GRADE_ABOVE_MAXIMUM",
                    "Grade must be less than or equal to " + AssignmentService.format(row.maxGrade()) + ".");
        }
        if (row.gradeItemId() != null && gradebook.isLocked(row.gradeItemId(), student.id())) {
            throw ApiException.conflict("GRADE_LOCKED", "This grade is locked or overridden in the gradebook.");
        }
        Instant now = clock.instant();
        AssignmentRepository.SubmissionRow latest = repository.latestSubmission(row.id(), student.id())
                .orElseGet(() -> repository.submission(
                        repository.insertSubmission(row.id(), student.id(), 0, SubmissionStatus.NEW, now)).orElseThrow());
        int attempt = in.attemptNumber() != null ? in.attemptNumber() : latest.attemptNumber();
        if (repository.attempt(row.id(), student.id(), attempt).isEmpty()) {
            throw ApiException.notFound("UNKNOWN_ATTEMPT", "The student has no attempt " + attempt);
        }
        repository.saveGrade(row.id(), student.id(), attempt, grade, grader.id(), now);

        boolean added = false;
        if (attempt == latest.attemptNumber()) {
            if (row.gradeItemId() != null) {
                gradebook.pushGrade(row.gradeItemId(), student.id(), grade, grader.id());
            }
            graded(row, student.id(), grade);
            if (grade != null) {
                added = reopenIfRequired(row, student.id(), latest, grade, Boolean.TRUE.equals(in.addAttempt()));
            }
        }
        int current = repository.latestSubmission(row.id(), student.id()).orElseThrow().attemptNumber();
        return new GradeResult(student.username(), attempt, grade, current, added);
    }

    /**
     * "Allow another attempt" as a direct action (Moodle {@code add_attempt()}, REQ-ASG-06): needs a submission row and
     * room under the attempt limit.
     */
    @Transactional
    GradeResult addAttempt(String caller, long assignmentId, String username) {
        AssignmentRepository.AssignmentRow row = assignments.require(assignmentId);
        access.require(caller, row.courseId(), Capability.ASSIGN_GRADE);
        UserAccount student = assignments.requireParticipant(row, username);
        AssignmentRepository.SubmissionRow latest = repository.latestSubmission(row.id(), student.id())
                .orElseThrow(() -> ApiException.conflict("NO_SUBMISSION", "The student has no submission yet."));
        if (!attemptsLeft(row, latest)) {
            throw ApiException.conflict("MAX_ATTEMPTS_REACHED", "The maximum number of attempts has been reached.");
        }
        int next = newAttempt(row, student.id(), latest);
        return new GradeResult(student.username(), latest.attemptNumber(),
                repository.grade(row.id(), student.id(), latest.attemptNumber()).map(AssignmentRepository.GradeRow::grade)
                        .orElse(null),
                next, true);
    }

    /**
     * "Revert the submission to draft" (REQ-ASG-08, fix of MDL-87490). The same attempt becomes a draft again and keeps
     * its files; unlike Moodle 5.3.1 no grade update runs, so no empty new attempt is opened.
     */
    @Transactional
    void revertToDraft(String caller, long assignmentId, String username) {
        AssignmentRepository.AssignmentRow row = assignments.require(assignmentId);
        access.require(caller, row.courseId(), Capability.ASSIGN_GRADE);
        UserAccount student = assignments.requireParticipant(row, username);
        AssignmentRepository.SubmissionRow latest = repository.latestSubmission(row.id(), student.id())
                .filter(s -> s.status() == SubmissionStatus.SUBMITTED)
                .orElseThrow(() -> ApiException.conflict("NOT_SUBMITTED", "The submission is not submitted for grading."));
        repository.setStatus(latest.id(), SubmissionStatus.DRAFT);
        if (row.completion() == CompletionRule.SUBMIT) {
            completion.recordActivityCompletion(row.activityKey(), student.id(), CompletionState.INCOMPLETE);
        }
    }

    // ---- rules

    /** REQ-ASG-07, Moodle {@code reopen_submission_if_required()}. */
    private boolean reopenIfRequired(AssignmentRepository.AssignmentRow row, long userId,
            AssignmentRepository.SubmissionRow latest, double grade, boolean addAttempt) {
        boolean reopen = switch (row.reopenMethod()) {
            case AUTOMATIC -> true;
            case MANUAL -> addAttempt;
            // Gradebook "is_passed": false only when there is a grade to pass and the grade is below it.
            case UNTIL_PASS -> row.gradePass() != null && grade < row.gradePass();
        };
        if (reopen && attemptsLeft(row, latest)) {
            newAttempt(row, userId, latest);
            return true;
        }
        return false;
    }

    /** REQ-ASG-06: with a limit N, the last allowed attempt is N-1; -1 is unlimited. */
    static boolean attemptsLeft(AssignmentRepository.AssignmentRow row, AssignmentRepository.SubmissionRow latest) {
        return row.unlimitedAttempts() || latest.attemptNumber() < row.maxAttempts() - 1;
    }

    private int newAttempt(AssignmentRepository.AssignmentRow row, long userId, AssignmentRepository.SubmissionRow latest) {
        int next = latest.attemptNumber() + 1;
        repository.insertSubmission(row.id(), userId, next, SubmissionStatus.REOPENED, clock.instant());
        AssignmentRepository.FlagRow flags = repository.flags(row.id(), userId);
        if (flags.locked()) {
            // Moodle unlocks the submission when it adds an attempt.
            repository.saveFlags(row.id(), userId, new AssignmentRepository.FlagRow(false, flags.extensionDueDate()));
        }
        return next;
    }

    private void graded(AssignmentRepository.AssignmentRow row, long userId, Double grade) {
        if (row.completion() != CompletionRule.GRADE) {
            return;
        }
        CompletionState state;
        if (grade == null) {
            state = CompletionState.INCOMPLETE;
        } else if (row.gradePass() == null) {
            state = CompletionState.COMPLETE;
        } else {
            state = grade >= row.gradePass() ? CompletionState.COMPLETE_PASS : CompletionState.COMPLETE_FAIL;
        }
        completion.recordActivityCompletion(row.activityKey(), userId, state);
    }
}
