package com.figlms.course.internal;

import com.figlms.ApiException;
import com.figlms.course.api.AccessControl;
import com.figlms.course.api.CourseSummary;
import com.figlms.course.api.EnrolmentMethod;
import com.figlms.course.api.EnrolmentRule;
import com.figlms.course.api.UnknownCourseException;
import com.figlms.identity.api.Capability;
import com.figlms.identity.api.Role;
import com.figlms.identity.api.UserAccount;
import com.figlms.identity.api.UserDirectory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enrolment (REQ-CRS-05..08) and the extension point for other modules' rules ({@link EnrolmentRule}).
 */
@Service
@Transactional
class EnrolmentService {

    record ManualEnrolment(String username, Role role, Instant timeStart, Instant timeEnd) {
    }

    record SelfSettings(boolean enabled, String enrolmentKey, Integer maxEnrolled, Instant enrolStart, Instant enrolEnd,
            Long enrolPeriodSeconds, Boolean allowNew) {
    }

    record Participant(String username, String fullName, Set<Role> roles, List<EnrolmentView> enrolments,
            boolean active) {
    }

    record EnrolmentView(EnrolmentMethod method, String status, Instant timeStart, Instant timeEnd, boolean active) {
    }

    private final CourseRepository repository;
    private final AccessControl access;
    private final UserDirectory users;
    private final List<EnrolmentRule> rules;
    private final Clock clock;

    EnrolmentService(CourseRepository repository, AccessControl access, UserDirectory users, List<EnrolmentRule> rules,
            Clock clock) {
        this.repository = repository;
        this.access = access;
        this.users = users;
        this.rules = rules;
        this.clock = clock;
    }

    /**
     * Manual enrolment (REQ-CRS-05, REQ-CRS-07, REQ-IDN-05): the caller needs {@code enrol/manual:enrol} and may give
     * only the roles they may assign. Enrolling the same user again updates the existing enrolment.
     */
    EnrolmentView enrolManually(String caller, String shortname, ManualEnrolment input) {
        CourseSummary course = requireCourse(shortname);
        access.require(caller, course.id(), Capability.ENROL_MANUAL);
        Role role = input.role() == null ? Role.STUDENT : input.role();
        if (!assignableRoles(caller, course.id()).contains(role)) {
            throw ApiException.forbidden("ROLE_NOT_ASSIGNABLE", "You cannot assign the role " + role.moodleShortname()
                    + " in this course.");
        }
        UserAccount user = users.require(input.username());
        UserAccount modifier = users.require(caller);
        return enrolInternal(course.id(), user, role, input.timeStart(), input.timeEnd(), modifier.id());
    }

    /** Self enrolment (REQ-CRS-08): every condition of Moodle's {@code can_self_enrol()}, in the same order. */
    EnrolmentView enrolSelf(String caller, String shortname, String key) {
        CourseSummary course = requireCourse(shortname);
        UserAccount user = users.require(caller);
        CourseRepository.MethodRow method = repository.method(course.id(), EnrolmentMethod.SELF)
                .orElseThrow(() -> inactive());
        Instant now = clock.instant();
        if (repository.enrolment(method.id(), user.id()).isPresent() || !method.enabled() || !method.allowNew()) {
            throw inactive();
        }
        if (method.enrolStart() != null && method.enrolStart().isAfter(now)) {
            throw ApiException.invalid("ENROLMENT_NOT_STARTED",
                    "You cannot enrol yet; enrolment starts on " + DateTimeFormatter.ISO_INSTANT.format(method.enrolStart()));
        }
        if (method.enrolEnd() != null && method.enrolEnd().isBefore(now)) {
            throw ApiException.invalid("ENROLMENT_ENDED", "You cannot enrol any more, since enrolment ended on "
                    + DateTimeFormatter.ISO_INSTANT.format(method.enrolEnd()));
        }
        // The limit counts every user of the method, suspended ones too (Moodle behaviour, see course.md).
        if (method.maxEnrolled() > 0 && repository.countEnrolments(method.id()) >= method.maxEnrolled()) {
            throw ApiException.conflict("MAX_ENROLLED", "Maximum number of users allowed to self-enrol was already reached.");
        }
        if (!access.has(caller, course.id(), Capability.ENROL_SELF)) {
            throw ApiException.forbidden("FORBIDDEN", "You cannot enrol yourself in this course.");
        }
        if (method.enrolmentKey() != null && !method.enrolmentKey().isEmpty()
                && !Objects.equals(method.enrolmentKey(), key)) {
            throw ApiException.invalid("WRONG_ENROLMENT_KEY", "Incorrect enrolment key, please try again");
        }
        Instant end = method.enrolPeriodSeconds() != null && method.enrolPeriodSeconds() > 0
                ? now.plus(Duration.ofSeconds(method.enrolPeriodSeconds())) : null;
        return enrolThrough(method, course, user, method.defaultRole(), now, end, user.id());
    }

    /** Self enrolment settings ({@code enrol/self:config}). The method is created disabled with every new course. */
    CourseRepository.MethodRow configureSelf(String caller, String shortname, SelfSettings settings) {
        CourseSummary course = requireCourse(shortname);
        access.require(caller, course.id(), Capability.ENROL_SELF_CONFIG);
        CourseRepository.MethodRow method = repository.method(course.id(), EnrolmentMethod.SELF).orElseThrow();
        if (settings.enrolStart() != null && settings.enrolEnd() != null && !settings.enrolEnd().isAfter(settings.enrolStart())) {
            throw ApiException.invalid("END_BEFORE_START", "The enrolment end date cannot be earlier than the start date.");
        }
        int max = settings.maxEnrolled() == null ? 0 : settings.maxEnrolled();
        if (max < 0) {
            throw ApiException.invalid("INVALID_MAX_ENROLLED", "The maximum number of enrolled users cannot be negative.");
        }
        repository.updateSelfMethod(method.id(), settings.enabled(), settings.enrolPeriodSeconds(), settings.enrolStart(),
                settings.enrolEnd(), CategoryService.emptyToNull(settings.enrolmentKey()), max,
                settings.allowNew() == null || settings.allowNew());
        return repository.method(course.id(), EnrolmentMethod.SELF).orElseThrow();
    }

    /** Suspend or re-activate a user's manual enrolment. */
    EnrolmentView setStatus(String caller, String shortname, String username, boolean suspended) {
        CourseSummary course = requireCourse(shortname);
        access.require(caller, course.id(), Capability.ENROL_MANUAL);
        UserAccount user = users.require(username);
        UserAccount modifier = users.require(caller);
        CourseRepository.MethodRow method = repository.method(course.id(), EnrolmentMethod.MANUAL).orElseThrow();
        CourseRepository.EnrolmentRow row = repository.enrolment(method.id(), user.id())
                .orElseThrow(() -> ApiException.notFound("NOT_ENROLLED", username + " is not enrolled manually in " + shortname));
        repository.setEnrolmentStatus(row.id(), suspended ? "SUSPENDED" : "ACTIVE", modifier.id());
        return view(repository.enrolment(method.id(), user.id()).orElseThrow());
    }

    @Transactional(readOnly = true)
    List<Participant> participants(String caller, String shortname) {
        CourseSummary course = requireCourse(shortname);
        access.require(caller, course.id(), Capability.COURSE_VIEW_PARTICIPANTS);
        Instant now = clock.instant();
        List<Participant> result = new ArrayList<>();
        List<CourseRepository.EnrolmentRow> rows = repository.enrolmentsOfCourse(course.id());
        rows.stream().map(CourseRepository.EnrolmentRow::userId).distinct().forEach(userId -> {
            Optional<UserAccount> user = users.findById(userId);
            if (user.isEmpty()) {
                return;
            }
            List<EnrolmentView> views = rows.stream().filter(r -> r.userId() == userId).map(this::view).toList();
            result.add(new Participant(user.get().username(), user.get().fullName(),
                    repository.roles(course.id(), userId), views,
                    rows.stream().anyMatch(r -> r.userId() == userId && r.isActive(now))));
        });
        return result;
    }

    @Transactional(readOnly = true)
    Set<Role> assignableRoles(String caller, long courseId) {
        UserAccount user = users.require(caller);
        if (user.siteAdmin()) {
            return EnumSet.allOf(Role.class);
        }
        if (!access.has(caller, courseId, Capability.ROLE_ASSIGN)) {
            return EnumSet.noneOf(Role.class);
        }
        Set<Role> result = EnumSet.noneOf(Role.class);
        access.rolesIn(user.id(), courseId).forEach(r -> result.addAll(r.assignableRoles()));
        return result;
    }

    @Transactional(readOnly = true)
    boolean selfEnrolmentOpen(long courseId) {
        Instant now = clock.instant();
        return repository.method(courseId, EnrolmentMethod.SELF)
                .filter(m -> m.enabled() && m.allowNew())
                .filter(m -> m.enrolStart() == null || !m.enrolStart().isAfter(now))
                .filter(m -> m.enrolEnd() == null || !m.enrolEnd().isBefore(now))
                .isPresent();
    }

    /** Manual enrolment without the caller checks; used for the creator of a new course and by {@link #enrolManually}. */
    EnrolmentView enrolInternal(long courseId, UserAccount user, Role role, Instant timeStart, Instant timeEnd,
            long modifiedBy) {
        CourseSummary course = repository.course(courseId).orElseThrow();
        CourseRepository.MethodRow method = repository.method(courseId, EnrolmentMethod.MANUAL).orElseThrow();
        if (!method.enabled()) {
            throw ApiException.invalid("METHOD_DISABLED", "Manual enrolments are disabled in this course.");
        }
        return enrolThrough(method, course, user, role, timeStart, timeEnd, modifiedBy);
    }

    private EnrolmentView enrolThrough(CourseRepository.MethodRow method, CourseSummary course, UserAccount user, Role role,
            Instant timeStart, Instant timeEnd, long modifiedBy) {
        if (timeStart != null && timeEnd != null && !timeEnd.isAfter(timeStart)) {
            // REQ-CRS-09 (b). Moodle stores this and the enrolment never becomes active.
            throw ApiException.invalid("INVALID_ENROLMENT_PERIOD", "The enrolment end must be after the enrolment start.");
        }
        EnrolmentRule.EnrolmentRequest request = new EnrolmentRule.EnrolmentRequest(user.id(), user.username(),
                course.id(), course.shortname(), role, method.method());
        for (EnrolmentRule rule : rules) {
            Optional<EnrolmentRule.Refusal> refusal = rule.check(request);
            if (refusal.isPresent()) {
                throw new ApiException(HttpStatus.CONFLICT, refusal.get().code(), refusal.get().message());
            }
        }
        Optional<CourseRepository.EnrolmentRow> existing = repository.enrolment(method.id(), user.id());
        if (existing.isPresent()) {
            repository.updateEnrolment(existing.get().id(), "ACTIVE", timeStart, timeEnd, modifiedBy);
        } else {
            repository.insertEnrolment(method.id(), user.id(), timeStart, timeEnd, clock.instant(), modifiedBy);
        }
        repository.assignRole(course.id(), user.id(), role);
        return view(repository.enrolment(method.id(), user.id()).orElseThrow());
    }

    private EnrolmentView view(CourseRepository.EnrolmentRow row) {
        return new EnrolmentView(row.method(), row.status(), row.timeStart(), row.timeEnd(), row.isActive(clock.instant()));
    }

    private CourseSummary requireCourse(String shortname) {
        return repository.courseByShortname(shortname).orElseThrow(() -> new UnknownCourseException(shortname));
    }

    private static ApiException inactive() {
        return ApiException.invalid("ENROLMENT_INACTIVE", "Enrolment is disabled or inactive");
    }
}
