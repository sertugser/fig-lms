package com.figlms.assignment.internal;

import com.figlms.ApiException;
import com.figlms.advising.api.CompletionService;
import com.figlms.advising.api.CompletionState;
import com.figlms.course.api.AccessControl;
import com.figlms.files.api.FileNames;
import com.figlms.files.api.FileStore;
import com.figlms.files.api.StoredFile;
import com.figlms.grading.api.GradeItemInfo;
import com.figlms.grading.api.GradebookService;
import com.figlms.identity.api.Capability;
import com.figlms.identity.api.UserAccount;
import com.figlms.identity.api.UserDirectory;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * The student's side of an assignment (REQ-ASG-01..05): the submission status page, saving work (online text and
 * files) and submitting it for grading.
 */
@Service
class SubmissionService {

    static final String COMPONENT = "assignment";
    static final String AREA = "submission";

    record FileView(long id, String filename, String mimetype, long size, String url) {
    }

    record Refusal(String code, String message) {
    }

    record AttemptView(int attemptNumber, String status, Instant timeModified, Double grade) {
    }

    /**
     * @param status            {@code NEW} when the student has no submission yet
     * @param late              REQ-ASG-02: last change strictly after the effective due date
     * @param timeRemaining     seconds until the effective due date; negative = overdue; {@code null} = no due date
     * @param editRefusal       why the student cannot change the work now; {@code null} = can edit
     * @param statement         text to accept before submitting; {@code null} = none needed
     * @param grade             grade of the current attempt, {@code null} if none or hidden in the gradebook
     */
    record SubmissionView(long assignmentId, String username, int attemptNumber, String status, String onlineText,
            List<FileView> files, Instant timeModified, boolean late, Instant allowSubmissionsFrom,
            Instant effectiveDueDate, Instant cutoffDate, Instant extensionDueDate, boolean locked,
            Long timeRemaining, boolean canEdit, Refusal editRefusal, boolean canSubmit, String statement,
            Double grade, double maxGrade, int maxAttempts, List<AttemptView> history) {
    }

    private final AssignmentService assignments;
    private final AssignmentRepository repository;
    private final AssignmentProperties properties;
    private final AccessControl access;
    private final UserDirectory users;
    private final GradebookService gradebook;
    private final CompletionService completion;
    private final FileStore files;
    private final Clock clock;

    SubmissionService(AssignmentService assignments, AssignmentRepository repository, AssignmentProperties properties,
            AccessControl access, UserDirectory users, GradebookService gradebook, CompletionService completion,
            FileStore files, Clock clock) {
        this.assignments = assignments;
        this.repository = repository;
        this.properties = properties;
        this.access = access;
        this.users = users;
        this.gradebook = gradebook;
        this.completion = completion;
        this.files = files;
        this.clock = clock;
    }

    // ---- status page

    /** The caller's own submission status. */
    @Transactional(readOnly = true)
    SubmissionView status(String caller, long assignmentId) {
        AssignmentRepository.AssignmentRow row = assignments.require(assignmentId);
        assignments.requireView(caller, row);
        return view(row, users.require(caller), caller);
    }

    /** A student's submission status as a grader sees it (needs {@code mod/assign:grade}). */
    @Transactional(readOnly = true)
    SubmissionView statusOf(String caller, long assignmentId, String username) {
        AssignmentRepository.AssignmentRow row = assignments.require(assignmentId);
        access.require(caller, row.courseId(), Capability.ASSIGN_GRADE);
        return view(row, assignments.requireParticipant(row, username), username);
    }

    SubmissionView view(AssignmentRepository.AssignmentRow row, UserAccount user, String username) {
        Instant now = clock.instant();
        Window window = assignments.window(row, user.id());
        Optional<AssignmentRepository.SubmissionRow> submission = repository.latestSubmission(row.id(), user.id());
        Optional<Refusal> refusal = editRefusal(row, user, username, submission.orElse(null), window, now);
        boolean hasContent = submission.map(this::hasContent).orElse(false);
        boolean canSubmit = row.submissionDrafts() && refusal.isEmpty() && hasContent
                && submission.get().status() != SubmissionStatus.SUBMITTED;
        Instant due = window.effectiveDueDate();
        List<AttemptView> history = new ArrayList<>();
        for (AssignmentRepository.SubmissionRow attempt : repository.attempts(row.id(), user.id())) {
            if (!attempt.latest()) {
                history.add(new AttemptView(attempt.attemptNumber(), attempt.status().name(), attempt.timeModified(),
                        visibleGrade(row, user.id(), attempt.attemptNumber())));
            }
        }
        return new SubmissionView(row.id(), user.username(),
                submission.map(AssignmentRepository.SubmissionRow::attemptNumber).orElse(0),
                submission.map(s -> s.status().name()).orElse(SubmissionStatus.NEW.name()),
                submission.map(AssignmentRepository.SubmissionRow::onlineText).orElse(null),
                submission.map(s -> fileViews(s.id())).orElse(List.of()),
                submission.map(AssignmentRepository.SubmissionRow::timeModified).orElse(null),
                submission.filter(s -> s.status() == SubmissionStatus.SUBMITTED)
                        .map(s -> window.isLate(s.timeModified())).orElse(false),
                window.allowSubmissionsFrom(), due, window.cutoffDate(), window.extensionDueDate(), window.locked(),
                due == null ? null : Duration.between(now, due).toSeconds(),
                refusal.isEmpty(), refusal.orElse(null), canSubmit, statementToAccept(row),
                submission.map(s -> visibleGrade(row, user.id(), s.attemptNumber())).orElse(null), row.maxGrade(),
                row.maxAttempts(), history);
    }

    // ---- saving and submitting

    /**
     * Saves the student's work (REQ-ASG-03..05, Moodle {@code save_submission()}): the new online text, the files kept
     * from before minus {@code removeFileIds}, plus the uploads. The result is a draft when the assignment requires
     * the submit button, otherwise it is submitted right away.
     */
    @Transactional
    SubmissionView save(String caller, long assignmentId, String onlineText, List<Long> removeFileIds,
            List<MultipartFile> uploads) {
        AssignmentRepository.AssignmentRow row = assignments.require(assignmentId);
        assignments.requireView(caller, row);
        UserAccount user = users.require(caller);
        Instant now = clock.instant();
        Window window = assignments.window(row, user.id());
        AssignmentRepository.SubmissionRow submission = repository.latestSubmission(row.id(), user.id()).orElse(null);
        editRefusal(row, user, caller, submission, window, now).ifPresent(SubmissionService::refuse);

        boolean textEnabled = onlineTextActive(row);
        boolean filesEnabled = fileSubmissionsActive(row);
        if (!textEnabled && !filesEnabled) {
            throw ApiException.conflict("NO_SUBMISSION_TYPES", "This assignment does not accept submissions online.");
        }
        List<MultipartFile> newFiles = uploads == null ? List.of() : uploads.stream().filter(f -> !f.isEmpty()).toList();
        if (!filesEnabled && !newFiles.isEmpty()) {
            throw ApiException.invalid("FILE_SUBMISSIONS_DISABLED", "This assignment does not accept file submissions.");
        }
        String text = textEnabled ? (onlineText != null ? onlineText
                : submission != null ? submission.onlineText() : null) : null;

        // Files after the save: kept ones plus new ones (a new file replaces a kept one of the same name).
        List<StoredFile> existing = submission == null ? List.of() : files.list(COMPONENT, AREA, submission.id());
        Set<Long> remove = removeFileIds == null ? Set.of() : new HashSet<>(removeFileIds);
        Set<String> names = new HashSet<>();
        long maxBytes = assignments.maxUploadBytes(row);
        for (MultipartFile upload : newFiles) {
            String name = FileNames.clean(upload.getOriginalFilename());
            if (name.isEmpty()) {
                throw ApiException.invalid("INVALID_FILENAME", "Invalid file name");
            }
            if (upload.getSize() > maxBytes) {
                throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                        "The file " + name + " is too large. The maximum size you can upload is " + maxBytes + " bytes.");
            }
            if (!FileNames.hasAcceptedType(name, row.acceptedTypes())) {
                throw ApiException.invalid("FILE_TYPE_NOT_ACCEPTED", name + " filetype cannot be accepted.");
            }
            names.add(name);
        }
        long kept = existing.stream().filter(f -> !remove.contains(f.id()) && !names.contains(f.filename())).count();
        if (filesEnabled && kept + names.size() > row.maxFiles()) {
            throw ApiException.invalid("TOO_MANY_FILES", "You can upload at most " + row.maxFiles() + " file(s).");
        }
        boolean hasText = text != null && !text.isBlank();
        if (!hasText && kept + names.size() == 0) {
            throw ApiException.invalid("SUBMISSION_EMPTY", "Nothing was submitted");
        }

        long submissionId = submission != null ? submission.id()
                : repository.insertSubmission(row.id(), user.id(), 0, SubmissionStatus.NEW, now);
        for (StoredFile file : existing) {
            if (remove.contains(file.id())) {
                files.delete(file.id());
            }
        }
        for (MultipartFile upload : newFiles) {
            try (InputStream in = upload.getInputStream()) {
                files.store(COMPONENT, AREA, submissionId, upload.getOriginalFilename(), upload.getContentType(), in,
                        user.id());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        SubmissionStatus status = row.submissionDrafts() ? SubmissionStatus.DRAFT : SubmissionStatus.SUBMITTED;
        repository.updateSubmission(submissionId, status, hasText ? text : null, now);
        if (status == SubmissionStatus.SUBMITTED) {
            submitted(row, user.id());
        }
        return view(row, user, caller);
    }

    /**
     * "Submit assignment" with drafts on (REQ-ASG-04, Moodle {@code submit_for_grading()}): the statement must be
     * accepted when there is one, and the draft must have content.
     */
    @Transactional
    SubmissionView submit(String caller, long assignmentId, boolean acceptStatement) {
        AssignmentRepository.AssignmentRow row = assignments.require(assignmentId);
        assignments.requireView(caller, row);
        UserAccount user = users.require(caller);
        if (!row.submissionDrafts()) {
            throw ApiException.conflict("SUBMIT_NOT_NEEDED", "Saved work is submitted at once in this assignment.");
        }
        Instant now = clock.instant();
        AssignmentRepository.SubmissionRow submission = repository.latestSubmission(row.id(), user.id()).orElse(null);
        editRefusal(row, user, caller, submission, assignments.window(row, user.id()), now)
                .ifPresent(SubmissionService::refuse);
        if (submission == null || !hasContent(submission)) {
            throw ApiException.invalid("SUBMISSION_EMPTY", "Nothing was submitted");
        }
        if (statementToAccept(row) != null && !acceptStatement) {
            throw ApiException.invalid("STATEMENT_REQUIRED",
                    "You are required to agree to this statement before you can submit.");
        }
        repository.updateSubmission(submission.id(), SubmissionStatus.SUBMITTED, submission.onlineText(), now);
        submitted(row, user.id());
        return view(row, user, caller);
    }

    // ---- rules

    /**
     * REQ-ASG-03: why the student may not change their work now, in Moodle's order of checks
     * ({@code can_edit_submission()} and {@code submissions_open()}); empty = may edit.
     */
    Optional<Refusal> editRefusal(AssignmentRepository.AssignmentRow row, UserAccount user, String username,
            AssignmentRepository.SubmissionRow submission, Window window, Instant now) {
        if (!access.isEnrolled(user.id(), row.courseId(), true) || !access.has(username, row.courseId(), Capability.ASSIGN_SUBMIT)) {
            return Optional.of(new Refusal("NO_SUBMIT_PERMISSION", "You do not have permission to submit work for this assignment."));
        }
        if (window.locked()) {
            return Optional.of(new Refusal("SUBMISSION_LOCKED", "This assignment is not accepting submissions"));
        }
        if (window.notYetOpen(now)) {
            return Optional.of(new Refusal("NOT_YET_OPEN", "This assignment will accept submissions from "
                    + window.allowSubmissionsFrom()));
        }
        if (!window.isOpen(now)) {
            return Optional.of(new Refusal("SUBMISSIONS_CLOSED", "Submissions closed"));
        }
        if (row.submissionDrafts() && submission != null && submission.status() == SubmissionStatus.SUBMITTED) {
            return Optional.of(new Refusal("ALREADY_SUBMITTED",
                    "This submission has been submitted for grading and cannot be changed."));
        }
        if (row.gradeItemId() != null && gradebook.isLocked(row.gradeItemId(), user.id())) {
            return Optional.of(new Refusal("GRADE_LOCKED", "This grade is locked or overridden in the gradebook."));
        }
        return Optional.empty();
    }

    boolean fileSubmissionsActive(AssignmentRepository.AssignmentRow row) {
        return properties.fileSubmissions() && row.fileEnabled() && row.maxFiles() > 0;
    }

    boolean onlineTextActive(AssignmentRepository.AssignmentRow row) {
        return properties.onlineTextSubmissions() && row.onlineTextEnabled();
    }

    private String statementToAccept(AssignmentRepository.AssignmentRow row) {
        String statement = properties.submissionStatement();
        return row.requireStatement() && statement != null && !statement.isBlank() ? statement : null;
    }

    private boolean hasContent(AssignmentRepository.SubmissionRow s) {
        return (s.onlineText() != null && !s.onlineText().isBlank()) || !files.list(COMPONENT, AREA, s.id()).isEmpty();
    }

    private void submitted(AssignmentRepository.AssignmentRow row, long userId) {
        if (row.completion() == CompletionRule.SUBMIT) {
            completion.recordActivityCompletion(row.activityKey(), userId, CompletionState.COMPLETE);
        }
    }

    private Double visibleGrade(AssignmentRepository.AssignmentRow row, long userId, int attemptNumber) {
        if (row.gradeItemId() != null && gradebook.item(row.gradeItemId()).map(GradeItemInfo::hidden).orElse(false)) {
            return null;
        }
        return repository.grade(row.id(), userId, attemptNumber).map(AssignmentRepository.GradeRow::grade).orElse(null);
    }

    private List<FileView> fileViews(long submissionId) {
        return files.list(COMPONENT, AREA, submissionId).stream()
                .map(f -> new FileView(f.id(), f.filename(), f.mimetype(), f.size(), "/api/files/" + f.id())).toList();
    }

    private static void refuse(Refusal r) {
        HttpStatus status = r.code().equals("NO_SUBMIT_PERMISSION") ? HttpStatus.FORBIDDEN : HttpStatus.CONFLICT;
        throw new ApiException(status, r.code(), r.message());
    }
}
