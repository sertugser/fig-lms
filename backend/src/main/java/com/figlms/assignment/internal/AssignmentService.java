package com.figlms.assignment.internal;

import com.figlms.ApiException;
import com.figlms.advising.api.Availability;
import com.figlms.advising.api.AvailabilityService;
import com.figlms.advising.api.CompletionService;
import com.figlms.course.api.AccessControl;
import com.figlms.course.api.CourseCatalog;
import com.figlms.course.api.CourseSummary;
import com.figlms.grading.api.GradebookService;
import com.figlms.identity.api.Capability;
import com.figlms.identity.api.UserAccount;
import com.figlms.identity.api.UserDirectory;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assignment settings, date overrides, extensions and locks (REQ-ASG-01, REQ-ASG-03). Creating an assignment also
 * creates its grade item and registers it as an activity for completion and access restrictions.
 */
@Service
@EnableConfigurationProperties(AssignmentProperties.class)
class AssignmentService {

    /**
     * Settings sent by the teacher; {@code null} fields take the Moodle default (on create) or stay (on update). A date
     * of {@code 1970-01-01T00:00:00Z} (Moodle's 0) disables that boundary; a grade to pass of 0 removes it.
     */
    record AssignmentInput(String name, String intro, Instant allowSubmissionsFrom, Instant dueDate, Instant cutoffDate,
            Boolean submissionDrafts, Boolean requireStatement, Double maxGrade, Double gradePass,
            ReopenMethod reopenMethod, Integer maxAttempts, Boolean onlineTextEnabled, Boolean fileEnabled,
            Integer maxFiles, Long maxBytes, List<String> acceptedTypes, CompletionRule completion,
            String availability) {
    }

    record AssignmentView(long id, String course, String name, String intro, Instant allowSubmissionsFrom,
            Instant dueDate, Instant cutoffDate, boolean submissionDrafts, boolean requireStatement, double maxGrade,
            Double gradePass, ReopenMethod reopenMethod, int maxAttempts, boolean onlineTextEnabled,
            boolean fileEnabled, int maxFiles, long maxUploadBytes, List<String> acceptedTypes,
            CompletionRule completion, String availability, Long gradeItemId, boolean available,
            String availabilityInfo) {
    }

    record DateOverride(Instant allowSubmissionsFrom, Instant dueDate, Instant cutoffDate) {
    }

    /** Moodle's limit on {@code maxattempts} in the settings form. */
    static final int MAX_ATTEMPTS_LIMIT = 30;

    private final AssignmentRepository repository;
    private final CourseCatalog courses;
    private final AccessControl access;
    private final UserDirectory users;
    private final GradebookService gradebook;
    private final CompletionService completion;
    private final AvailabilityService availability;
    private final com.figlms.files.api.FileStore files;
    private final Clock clock;

    AssignmentService(AssignmentRepository repository, CourseCatalog courses, AccessControl access,
            UserDirectory users, GradebookService gradebook, CompletionService completion,
            AvailabilityService availability, com.figlms.files.api.FileStore files, Clock clock) {
        this.repository = repository;
        this.courses = courses;
        this.access = access;
        this.users = users;
        this.gradebook = gradebook;
        this.completion = completion;
        this.availability = availability;
        this.files = files;
        this.clock = clock;
    }

    // ---- settings

    @Transactional
    AssignmentView create(String caller, String shortname, AssignmentInput in) {
        CourseSummary course = courses.require(shortname);
        access.require(caller, course.id(), Capability.MANAGE_ACTIVITIES);
        AssignmentRepository.AssignmentRow row = merge(new AssignmentRepository.AssignmentRow(0, course.id(), null, "",
                null, null, null, false, false, 100, null, ReopenMethod.UNTIL_PASS, 1, false, true, 20, 0, List.of(),
                CompletionRule.NONE, null, null), in);
        long id = repository.insert(row, clock.instant());
        String key = "assignment:" + id;
        long itemId = gradebook.createActivityItem(course.id(), key, row.name(), 0, row.maxGrade(), row.gradePass());
        repository.setGradeItem(id, itemId);
        completion.registerActivity(course.id(), key, row.name());
        return view(caller, require(id));
    }

    @Transactional
    AssignmentView update(String caller, long id, AssignmentInput in) {
        AssignmentRepository.AssignmentRow old = require(id);
        access.require(caller, old.courseId(), Capability.MANAGE_ACTIVITIES);
        AssignmentRepository.AssignmentRow row = merge(old, in);
        repository.update(row, clock.instant());
        if (row.gradeItemId() != null) {
            gradebook.updateActivityItem(row.gradeItemId(), row.name(), row.maxGrade(), row.gradePass());
        }
        completion.registerActivity(row.courseId(), row.activityKey(), row.name());
        return view(caller, require(id));
    }

    /** Assignments of the course the caller can see (hidden by a restriction = left out, REQ-ADV-03). */
    @Transactional(readOnly = true)
    List<AssignmentView> list(String caller, String shortname) {
        CourseSummary course = courses.require(shortname);
        access.requireCourseAccess(caller, course.id());
        List<AssignmentView> result = new ArrayList<>();
        for (AssignmentRepository.AssignmentRow row : repository.inCourse(course.id())) {
            Availability a = availability.evaluate(row.availability(), row.courseId(), caller);
            if (a.available() || a.shownOnCoursePage()) {
                result.add(view(row, course, a));
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    AssignmentView get(String caller, long id) {
        AssignmentRepository.AssignmentRow row = require(id);
        requireView(caller, row);
        return view(caller, row);
    }

    // ---- overrides, extensions, locks

    @Transactional
    void setOverride(String caller, long id, String username, DateOverride dates) {
        AssignmentRepository.AssignmentRow row = require(id);
        access.require(caller, row.courseId(), Capability.MANAGE_ACTIVITIES);
        UserAccount user = requireParticipant(row, username);
        AssignmentRepository.AssignmentRow effective = new AssignmentRepository.AssignmentRow(row.id(), row.courseId(),
                row.name(), row.intro(), first(dates.allowSubmissionsFrom(), row.allowSubmissionsFrom()),
                first(dates.dueDate(), row.dueDate()), first(dates.cutoffDate(), row.cutoffDate()),
                row.submissionDrafts(), row.requireStatement(), row.maxGrade(), row.gradePass(), row.reopenMethod(),
                row.maxAttempts(), row.onlineTextEnabled(), row.fileEnabled(), row.maxFiles(), row.maxBytes(),
                row.acceptedTypes(), row.completion(), row.availability(), row.gradeItemId());
        validateDates(effective);
        repository.saveOverride(id, user.id(),
                new AssignmentRepository.OverrideRow(dates.allowSubmissionsFrom(), dates.dueDate(), dates.cutoffDate()));
    }

    @Transactional
    void deleteOverride(String caller, long id, String username) {
        AssignmentRepository.AssignmentRow row = require(id);
        access.require(caller, row.courseId(), Capability.MANAGE_ACTIVITIES);
        repository.deleteOverride(id, users.require(username).id());
    }

    /**
     * Grants (or with {@code null} removes) an extension (Moodle {@code mod/assign:grantextension}, held by teachers,
     * which {@link Capability#ASSIGN_GRADE} matches). Like Moodle, the extension must lie after the due date and after
     * the opening date.
     */
    @Transactional
    void setExtension(String caller, long id, String username, Instant extensionDueDate) {
        AssignmentRepository.AssignmentRow row = require(id);
        access.require(caller, row.courseId(), Capability.ASSIGN_GRADE);
        UserAccount user = requireParticipant(row, username);
        Window window = window(row, user.id());
        if (extensionDueDate != null) {
            if (window.dueDate() != null && !extensionDueDate.isAfter(window.dueDate())) {
                throw ApiException.invalid("EXTENSION_NOT_AFTER_DUE_DATE", "Extension date must be after the due date");
            }
            if (window.allowSubmissionsFrom() != null && !extensionDueDate.isAfter(window.allowSubmissionsFrom())) {
                throw ApiException.invalid("EXTENSION_NOT_AFTER_FROM_DATE",
                        "Extension date must be after the allow submissions from date");
            }
        }
        AssignmentRepository.FlagRow flags = repository.flags(id, user.id());
        repository.saveFlags(id, user.id(), new AssignmentRepository.FlagRow(flags.locked(), extensionDueDate));
    }

    /** "Prevent submission changes" (REQ-ASG-03); Moodle checks {@code mod/assign:grade} for it. */
    @Transactional
    void setLocked(String caller, long id, String username, boolean locked) {
        AssignmentRepository.AssignmentRow row = require(id);
        access.require(caller, row.courseId(), Capability.ASSIGN_GRADE);
        UserAccount user = requireParticipant(row, username);
        AssignmentRepository.FlagRow flags = repository.flags(id, user.id());
        repository.saveFlags(id, user.id(), new AssignmentRepository.FlagRow(locked, flags.extensionDueDate()));
    }

    // ---- shared by the other services

    AssignmentRepository.AssignmentRow require(long id) {
        return repository.find(id).orElseThrow(
                () -> ApiException.notFound("UNKNOWN_ASSIGNMENT", "Unknown assignment " + id));
    }

    Window window(AssignmentRepository.AssignmentRow row, long userId) {
        return Window.of(row, repository.override(row.id(), userId).orElse(null), repository.flags(row.id(), userId));
    }

    /**
     * May the caller open the assignment? Course access, {@code mod/assign:view} and the access restriction
     * (REQ-ADV-01); users who may ignore restrictions pass the last check (REQ-ADV-07).
     */
    void requireView(String caller, AssignmentRepository.AssignmentRow row) {
        access.requireCourseAccess(caller, row.courseId());
        access.require(caller, row.courseId(), Capability.ASSIGN_VIEW);
        Availability a = availability.evaluate(row.availability(), row.courseId(), caller);
        if (!a.available()) {
            throw ApiException.forbidden("NOT_AVAILABLE", a.information());
        }
    }

    /** The user must be enrolled in the course (any enrolment, as Moodle lists suspended students too). */
    UserAccount requireParticipant(AssignmentRepository.AssignmentRow row, String username) {
        UserAccount user = users.require(username);
        if (!access.isEnrolled(user.id(), row.courseId(), false)) {
            throw ApiException.notFound("NOT_A_PARTICIPANT", username + " is not enrolled in this course");
        }
        return user;
    }

    long maxUploadBytes(AssignmentRepository.AssignmentRow row) {
        return files.maxUploadBytes(0, row.maxBytes());
    }

    // ---- helpers

    private AssignmentView view(String caller, AssignmentRepository.AssignmentRow row) {
        CourseSummary course = courses.findById(row.courseId()).orElseThrow();
        return view(row, course, availability.evaluate(row.availability(), row.courseId(), caller));
    }

    private AssignmentView view(AssignmentRepository.AssignmentRow row, CourseSummary course, Availability a) {
        return new AssignmentView(row.id(), course.shortname(), row.name(), row.intro(), row.allowSubmissionsFrom(),
                row.dueDate(), row.cutoffDate(), row.submissionDrafts(), row.requireStatement(), row.maxGrade(),
                row.gradePass(), row.reopenMethod(), row.maxAttempts(), row.onlineTextEnabled(), row.fileEnabled(),
                row.maxFiles(), maxUploadBytes(row), row.acceptedTypes(), row.completion(), row.availability(),
                row.gradeItemId(), a.available(), a.information());
    }

    private AssignmentRepository.AssignmentRow merge(AssignmentRepository.AssignmentRow old, AssignmentInput in) {
        String name = in.name() != null ? in.name().trim() : old.name();
        if (name == null || name.isEmpty()) {
            throw ApiException.invalid("NAME_REQUIRED", "You must supply a value here.");
        }
        if (name.length() > 255) {
            throw ApiException.invalid("NAME_TOO_LONG", "The name can have at most 255 characters.");
        }
        double maxGrade = first(in.maxGrade(), old.maxGrade());
        if (maxGrade <= 0) {
            throw ApiException.invalid("INVALID_MAX_GRADE", "The maximum grade must be greater than zero.");
        }
        Double gradePass = in.gradePass() != null ? (in.gradePass() == 0 ? null : in.gradePass()) : old.gradePass();
        if (gradePass != null && (gradePass < 0 || gradePass > maxGrade)) {
            throw ApiException.invalid("INVALID_GRADE_PASS", "The grade to pass can not be greater than the maximum possible grade " + format(maxGrade));
        }
        int maxAttempts = first(in.maxAttempts(), old.maxAttempts());
        if (maxAttempts != -1 && (maxAttempts < 1 || maxAttempts > MAX_ATTEMPTS_LIMIT)) {
            throw ApiException.invalid("INVALID_MAX_ATTEMPTS", "Maximum attempts must be -1 (unlimited) or 1 to " + MAX_ATTEMPTS_LIMIT + ".");
        }
        int maxFiles = first(in.maxFiles(), old.maxFiles());
        long maxBytes = first(in.maxBytes(), old.maxBytes());
        if (maxFiles < 0 || maxBytes < 0) {
            throw ApiException.invalid("INVALID_FILE_LIMITS", "File limits can not be negative.");
        }
        String restriction = in.availability() != null ? (in.availability().isBlank() ? null : in.availability())
                : old.availability();
        availability.validate(restriction);
        AssignmentRepository.AssignmentRow row = new AssignmentRepository.AssignmentRow(old.id(), old.courseId(), name,
                first(in.intro(), old.intro()),
                date(in.allowSubmissionsFrom(), old.allowSubmissionsFrom()), date(in.dueDate(), old.dueDate()),
                date(in.cutoffDate(), old.cutoffDate()),
                first(in.submissionDrafts(), old.submissionDrafts()), first(in.requireStatement(), old.requireStatement()),
                maxGrade, gradePass, first(in.reopenMethod(), old.reopenMethod()), maxAttempts,
                first(in.onlineTextEnabled(), old.onlineTextEnabled()), first(in.fileEnabled(), old.fileEnabled()),
                maxFiles, maxBytes, in.acceptedTypes() != null ? in.acceptedTypes() : old.acceptedTypes(),
                first(in.completion(), old.completion()), restriction, old.gradeItemId());
        validateDates(row);
        return row;
    }

    /** REQ-ASG-01 settings form validation (Moodle {@code mod_assign_mod_form::validation()}). */
    static void validateDates(AssignmentRepository.AssignmentRow a) {
        if (a.dueDate() != null && a.allowSubmissionsFrom() != null && !a.dueDate().isAfter(a.allowSubmissionsFrom())) {
            throw ApiException.invalid("DUE_DATE_BEFORE_FROM_DATE",
                    "Due date cannot be earlier than the allow submissions from date.");
        }
        if (a.cutoffDate() != null) {
            if (a.dueDate() != null && a.cutoffDate().isBefore(a.dueDate())) {
                throw ApiException.invalid("CUTOFF_BEFORE_DUE_DATE", "Cut-off date cannot be earlier than the due date.");
            }
            if (a.allowSubmissionsFrom() != null && a.cutoffDate().isBefore(a.allowSubmissionsFrom())) {
                throw ApiException.invalid("CUTOFF_BEFORE_FROM_DATE",
                        "Cut-off date cannot be earlier than the allow submissions from date.");
            }
        }
    }

    /** {@code null} keeps the old date; the epoch (Moodle's 0) disables the boundary. */
    private static Instant date(Instant value, Instant old) {
        if (value == null) {
            return old;
        }
        return value.equals(Instant.EPOCH) ? null : value;
    }

    static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static <T> T first(T value, T fallback) {
        return Optional.ofNullable(value).orElse(fallback);
    }
}
