package com.figlms.course.api;

import com.figlms.identity.api.Capability;
import com.figlms.identity.api.Role;
import java.util.List;
import java.util.Set;

/**
 * Who may do what in a course (REQ-IDN-03, REQ-IDN-04, REQ-CRS-06). The one place every module asks; Moodle spreads
 * the same question over {@code require_login()}, {@code is_enrolled()} and {@code has_capability()} calls in each page.
 *
 * <p>A user has a capability in a course when they are the site administrator, or when the capability is given to
 * every logged-in user, or when they have an <em>active</em> enrolment in the course and one of their roles there
 * grants it. A suspended or expired enrolment gives no capabilities, which has the effect of Moodle's
 * {@code require_login()} refusing the course.
 */
public interface AccessControl {

    boolean has(String username, long courseId, Capability capability);

    /** @throws com.figlms.ApiException 403 {@code FORBIDDEN} if the user lacks the capability */
    void require(String username, long courseId, Capability capability);

    /**
     * May the user enter the course at all (Moodle {@code require_login($course)})? Site administrators always;
     * others need an active enrolment, and for a hidden course also {@link Capability#COURSE_VIEW_HIDDEN}.
     *
     * @throws com.figlms.ApiException 403 {@code NOT_ENROLLED} otherwise
     */
    void requireCourseAccess(String username, long courseId);

    /** Roles of the user in the course, from role assignments; empty if none. */
    Set<Role> rolesIn(long userId, long courseId);

    /**
     * @param onlyActive {@code true}: only enrolments that give access now (REQ-CRS-06); {@code false}: any enrolment,
     *                   also suspended or outside its time window (Moodle {@code is_enrolled(..., false)})
     */
    boolean isEnrolled(long userId, long courseId, boolean onlyActive);

    /** Ids of users who hold {@code role} in the course, sorted; with {@code onlyActive} only active enrolments. */
    List<Long> usersWithRole(long courseId, Role role, boolean onlyActive);
}
