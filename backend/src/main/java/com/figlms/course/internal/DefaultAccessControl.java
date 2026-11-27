package com.figlms.course.internal;

import com.figlms.ApiException;
import com.figlms.course.api.AccessControl;
import com.figlms.course.api.CourseSummary;
import com.figlms.course.api.UnknownCourseException;
import com.figlms.identity.api.Capability;
import com.figlms.identity.api.Role;
import com.figlms.identity.api.UserAccount;
import com.figlms.identity.api.UserDirectory;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class DefaultAccessControl implements AccessControl {

    private final CourseRepository repository;
    private final UserDirectory users;
    private final Clock clock;

    DefaultAccessControl(CourseRepository repository, UserDirectory users, Clock clock) {
        this.repository = repository;
        this.users = users;
        this.clock = clock;
    }

    @Override
    public boolean has(String username, long courseId, Capability capability) {
        Optional<UserAccount> user = users.findByUsername(username);
        if (user.isEmpty() || user.get().suspended()) {
            return false;
        }
        if (user.get().siteAdmin() || capability.authenticatedUser()) {
            return true;
        }
        if (!isEnrolled(user.get().id(), courseId, true)) {
            return false;
        }
        return rolesIn(user.get().id(), courseId).stream().anyMatch(capability::grantedTo);
    }

    @Override
    public void require(String username, long courseId, Capability capability) {
        if (!has(username, courseId, capability)) {
            throw ApiException.forbidden("FORBIDDEN",
                    "Sorry, but you do not currently have permissions to do that (" + capability.moodleName() + ").");
        }
    }

    @Override
    public void requireCourseAccess(String username, long courseId) {
        CourseSummary course = repository.course(courseId).orElseThrow(() -> new UnknownCourseException("id " + courseId));
        UserAccount user = users.findByUsername(username).orElse(null);
        if (user == null || user.suspended()) {
            throw notEnrolled();
        }
        if (user.siteAdmin()) {
            return;
        }
        if (!isEnrolled(user.id(), courseId, true)) {
            throw notEnrolled();
        }
        if (!course.visible() && !has(username, courseId, Capability.COURSE_VIEW_HIDDEN)) {
            throw notEnrolled();
        }
    }

    @Override
    public Set<Role> rolesIn(long userId, long courseId) {
        return repository.roles(courseId, userId);
    }

    @Override
    public boolean isEnrolled(long userId, long courseId, boolean onlyActive) {
        Instant now = clock.instant();
        return repository.enrolments(courseId, userId).stream().anyMatch(e -> !onlyActive || e.isActive(now));
    }

    @Override
    public List<Long> usersWithRole(long courseId, Role role, boolean onlyActive) {
        return repository.usersWithRole(courseId, role).stream()
                .filter(userId -> isEnrolled(userId, courseId, onlyActive)).toList();
    }

    private static ApiException notEnrolled() {
        return ApiException.forbidden("NOT_ENROLLED", "Course or activity not accessible. You are not enrolled in this course.");
    }
}
