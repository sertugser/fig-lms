package com.figlms.course.internal;

import static com.figlms.course.internal.CategoryService.emptyToNull;

import com.figlms.ApiException;
import com.figlms.course.api.AccessControl;
import com.figlms.course.api.CourseSummary;
import com.figlms.course.api.EnrolmentMethod;
import com.figlms.course.api.UnknownCourseException;
import com.figlms.identity.api.Capability;
import com.figlms.identity.api.Role;
import com.figlms.identity.api.UserAccount;
import com.figlms.identity.api.UserDirectory;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creating and reading courses (REQ-CRS-01, REQ-CRS-02, REQ-CRS-03, REQ-CRS-07 contacts). */
@Service
@Transactional
class CourseService {

    static final int FULLNAME_MAX = 1333;
    static final int SHORTNAME_MAX = 255;
    static final int IDNUMBER_MAX = 100;

    record NewCourse(String fullname, String shortname, String idnumber, Long categoryId, Instant startdate,
            Instant enddate, Boolean visible) {
    }

    /** The course page header: what anyone who may see the course is shown (Moodle course summary box). */
    record CourseDetails(CourseSummary course, List<String> contacts, boolean selfEnrolmentOpen) {
    }

    record CourseDefaults(Instant startdate, Instant enddate) {
    }

    private final CourseRepository repository;
    private final CategoryService categories;
    private final EnrolmentService enrolments;
    private final AccessControl access;
    private final UserDirectory users;
    private final CourseProperties properties;
    private final Clock clock;

    CourseService(CourseRepository repository, CategoryService categories, EnrolmentService enrolments,
            AccessControl access, UserDirectory users, CourseProperties properties, Clock clock) {
        this.repository = repository;
        this.categories = categories;
        this.enrolments = enrolments;
        this.access = access;
        this.users = users;
        this.properties = properties;
        this.clock = clock;
    }

    CourseSummary create(String caller, NewCourse input) {
        UserAccount creator = users.require(caller);
        if (!creator.siteAdmin()) {
            throw ApiException.forbidden("FORBIDDEN", "Sorry, but you do not currently have permissions to do that ("
                    + Capability.COURSE_CREATE.moodleName() + ").");
        }
        String fullname = input.fullname() == null ? "" : input.fullname().strip();
        String shortname = input.shortname() == null ? "" : input.shortname().strip();
        String idnumber = emptyToNull(input.idnumber());
        if (fullname.isEmpty()) {
            throw ApiException.invalid("FULLNAME_REQUIRED", "Missing full name");
        }
        if (shortname.isEmpty()) {
            throw ApiException.invalid("SHORTNAME_REQUIRED", "Missing short name");
        }
        if (fullname.length() > FULLNAME_MAX || shortname.length() > SHORTNAME_MAX
                || (idnumber != null && idnumber.length() > IDNUMBER_MAX)) {
            throw ApiException.invalid("FIELD_TOO_LONG", "Full name, short name or ID number is too long");
        }
        if (input.categoryId() == null) {
            throw ApiException.invalid("CATEGORY_REQUIRED", "A course needs a category");
        }
        CourseRepository.CategoryRow category = categories.requireCategory(input.categoryId());
        validateDates(input.startdate(), input.enddate());
        if (repository.shortnameTaken(shortname)) {
            throw ApiException.conflict("SHORTNAME_TAKEN", "Short name is already used for another course (" + shortname + ")");
        }
        if (idnumber != null && repository.courseIdnumberTaken(idnumber)) {
            throw ApiException.conflict("COURSE_IDNUMBER_TAKEN", "ID number is already used for another course (" + idnumber + ")");
        }
        boolean visible = input.visible() != null ? input.visible() : category.visible();
        Instant now = clock.instant();
        long id = repository.insertCourse(category.id(), fullname, shortname, idnumber, input.startdate(), input.enddate(),
                visible, now);
        repository.insertMethod(id, EnrolmentMethod.MANUAL, true);
        repository.insertMethod(id, EnrolmentMethod.GUEST, false);
        repository.insertMethod(id, EnrolmentMethod.SELF, false);
        if (properties.enrolAdminInNewCourse()) {
            enrolments.enrolInternal(id, creator, Role.EDITING_TEACHER, null, null, creator.id());
        }
        return repository.course(id).orElseThrow();
    }

    /**
     * REQ-CRS-02: start date required; end date optional; end before start rejected, equal allowed; an end date
     * without a start date rejected.
     */
    static void validateDates(Instant start, Instant end) {
        if (start == null && end != null) {
            throw ApiException.invalid("NO_START_DATE_WITH_END_DATE", "A course with an end date must have a start date.");
        }
        if (start == null) {
            throw ApiException.invalid("STARTDATE_REQUIRED", "The course start date is required.");
        }
        if (end != null && end.isBefore(start)) {
            throw ApiException.invalid("END_BEFORE_START", "The course end date must be after the start date.");
        }
    }

    @Transactional(readOnly = true)
    CourseDetails details(String caller, String shortname) {
        CourseSummary course = require(shortname);
        if (!course.visible() && !access.has(caller, course.id(), Capability.COURSE_VIEW_HIDDEN)) {
            throw new UnknownCourseException(shortname);
        }
        List<String> contacts = access.usersWithRole(course.id(), Role.EDITING_TEACHER, true).stream()
                .map(users::findById).flatMap(java.util.Optional::stream)
                .map(u -> "Teacher: " + u.fullName()).toList();
        return new CourseDetails(course, contacts, enrolments.selfEnrolmentOpen(course.id()));
    }

    /** Form proposal for a new course (REQ-CRS-02): start tomorrow 00:00 (UTC), end one course duration later. */
    @Transactional(readOnly = true)
    CourseDefaults defaults() {
        Instant start = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new CourseDefaults(start, start.plus(properties.courseDuration()));
    }

    /** Course setting "Show gradebook to students" (REQ-GRD-06). */
    CourseSummary setShowGrades(String caller, String shortname, boolean showGrades) {
        CourseSummary course = require(shortname);
        access.require(caller, course.id(), Capability.COURSE_UPDATE);
        repository.setShowGrades(course.id(), showGrades);
        return repository.course(course.id()).orElseThrow();
    }

    @Transactional(readOnly = true)
    List<CourseSummary> myCourses(String caller) {
        UserAccount user = users.require(caller);
        return repository.courses().stream().filter(c -> access.isEnrolled(user.id(), c.id(), true)).toList();
    }

    CourseSummary require(String shortname) {
        return repository.courseByShortname(shortname).orElseThrow(() -> new UnknownCourseException(shortname));
    }
}
