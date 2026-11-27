package com.figlms.identity.api;

import static com.figlms.identity.api.Role.EDITING_TEACHER;
import static com.figlms.identity.api.Role.MANAGER;
import static com.figlms.identity.api.Role.STUDENT;
import static com.figlms.identity.api.Role.TEACHER;

import java.util.EnumSet;
import java.util.Set;

/**
 * What a role may do in a course (REQ-IDN-04). The default grants are copied from Moodle 5.3.1
 * ({@code lib/db/access.php}, {@code mod/assign/db/access.php}, {@code enrol/manual/db/access.php},
 * {@code grade/report/user/db/access.php}). Each constant names the Moodle capability it stands for.
 *
 * <p>The site administrator has every capability everywhere. Capabilities that Moodle gives to every logged-in user
 * (the {@code user} archetype) are marked {@link #authenticatedUser()}.
 */
public enum Capability {
    /** {@code moodle/course:create}, checked in the category. Only managers (and site admins) by default. */
    COURSE_CREATE("moodle/course:create", EnumSet.of(MANAGER)),
    /** {@code moodle/category:manage}. */
    CATEGORY_MANAGE("moodle/category:manage", EnumSet.of(MANAGER)),
    /** {@code moodle/course:update}: change course settings, such as the enrolment methods. */
    COURSE_UPDATE("moodle/course:update", EnumSet.of(EDITING_TEACHER, MANAGER)),
    /** {@code moodle/course:viewhiddencourses}. */
    COURSE_VIEW_HIDDEN("moodle/course:viewhiddencourses", EnumSet.of(TEACHER, EDITING_TEACHER, MANAGER)),
    /** {@code moodle/course:viewparticipants}. */
    COURSE_VIEW_PARTICIPANTS("moodle/course:viewparticipants", EnumSet.of(STUDENT, TEACHER, EDITING_TEACHER, MANAGER)),
    /** {@code enrol/manual:enrol} (REQ-IDN-05). */
    ENROL_MANUAL("enrol/manual:enrol", EnumSet.of(EDITING_TEACHER, MANAGER)),
    /** {@code enrol/self:config}: set up self enrolment in a course. */
    ENROL_SELF_CONFIG("enrol/self:config", EnumSet.of(EDITING_TEACHER, MANAGER)),
    /** {@code enrol/self:enrolself}: given to every logged-in user (archetype {@code user}). */
    ENROL_SELF("enrol/self:enrolself", EnumSet.noneOf(Role.class), true),
    /** {@code moodle/role:assign}: assign course roles to others, limited by {@link Role#assignableRoles()}. */
    ROLE_ASSIGN("moodle/role:assign", EnumSet.of(EDITING_TEACHER, MANAGER)),
    /** {@code moodle/course:manageactivities}: create and change activities such as assignments. */
    MANAGE_ACTIVITIES("moodle/course:manageactivities", EnumSet.of(EDITING_TEACHER, MANAGER)),
    /** {@code moodle/course:ignoreavailabilityrestrictions} (REQ-ADV-07). */
    IGNORE_AVAILABILITY("moodle/course:ignoreavailabilityrestrictions", EnumSet.of(TEACHER, EDITING_TEACHER, MANAGER)),
    /** {@code report/completion:view}: see other users' completion and prerequisite status (REQ-ADV-09). */
    COMPLETION_VIEW_REPORT("report/completion:view", EnumSet.of(TEACHER, EDITING_TEACHER, MANAGER)),
    /** {@code moodle/course:markcomplete}: mark a student complete (course completion criterion "role"). */
    MARK_COMPLETE("moodle/course:markcomplete", EnumSet.of(TEACHER, EDITING_TEACHER, MANAGER)),
    /** {@code mod/assign:view}. */
    ASSIGN_VIEW("mod/assign:view", EnumSet.of(STUDENT, TEACHER, EDITING_TEACHER, MANAGER)),
    /** {@code mod/assign:submit}. */
    ASSIGN_SUBMIT("mod/assign:submit", EnumSet.of(STUDENT)),
    /** {@code mod/assign:grade}: grade, grant attempts, revert to draft, extensions. */
    ASSIGN_GRADE("mod/assign:grade", EnumSet.of(TEACHER, EDITING_TEACHER, MANAGER)),
    /** {@code moodle/grade:view} together with {@code gradereport/user:view}: see one's own grades (REQ-GRD-06). */
    GRADE_VIEW_OWN("moodle/grade:view", EnumSet.of(STUDENT)),
    /** {@code moodle/grade:viewall} (REQ-GRD-08). */
    GRADE_VIEW_ALL("moodle/grade:viewall", EnumSet.of(TEACHER, EDITING_TEACHER, MANAGER)),
    /** {@code moodle/grade:viewhidden} (REQ-GRD-07). */
    GRADE_VIEW_HIDDEN("moodle/grade:viewhidden", EnumSet.of(TEACHER, EDITING_TEACHER, MANAGER)),
    /** {@code moodle/grade:edit} (REQ-GRD-08). */
    GRADE_EDIT("moodle/grade:edit", EnumSet.of(TEACHER, EDITING_TEACHER, MANAGER)),
    /** {@code moodle/grade:manage}: change grade items, categories and aggregation settings. */
    GRADE_MANAGE("moodle/grade:manage", EnumSet.of(EDITING_TEACHER, MANAGER));

    private final String moodleName;
    private final Set<Role> roles;
    private final boolean authenticatedUser;

    Capability(String moodleName, Set<Role> roles) {
        this(moodleName, roles, false);
    }

    Capability(String moodleName, Set<Role> roles, boolean authenticatedUser) {
        this.moodleName = moodleName;
        this.roles = roles;
        this.authenticatedUser = authenticatedUser;
    }

    public String moodleName() {
        return moodleName;
    }

    /** {@code true} if a holder of {@code role} has this capability by default. */
    public boolean grantedTo(Role role) {
        return roles.contains(role);
    }

    /** {@code true} if every logged-in user has this capability, whatever their roles (Moodle archetype {@code user}). */
    public boolean authenticatedUser() {
        return authenticatedUser;
    }
}
