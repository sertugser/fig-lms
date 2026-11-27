package com.figlms.identity.api;

import java.util.EnumSet;
import java.util.Set;

/**
 * Course-level roles (REQ-IDN-02). Each one is a Moodle role archetype; the project calls them student, teacher and
 * admin. A role is always held in one course (Moodle: a role assignment in the course context). The site
 * administrator is not a role here but a flag of the account ({@link UserAccount#siteAdmin()}), as in Moodle.
 */
public enum Role {
    /** Moodle {@code student}. */
    STUDENT("student"),
    /** Moodle {@code teacher} (non-editing teacher): grades, but does not change the course. */
    TEACHER("teacher"),
    /** Moodle {@code editingteacher}: the project role "teacher". */
    EDITING_TEACHER("editingteacher"),
    /** Moodle {@code manager}: the project role "admin" inside a course. */
    MANAGER("manager");

    private final String moodleShortname;

    Role(String moodleShortname) {
        this.moodleShortname = moodleShortname;
    }

    public String moodleShortname() {
        return moodleShortname;
    }

    /**
     * Roles a holder of this role may give to others (Moodle {@code role_allow_assign} defaults, REQ-CRS-07): an
     * editing teacher may assign non-editing teacher and student, but not editing teacher; a manager may assign all.
     */
    public Set<Role> assignableRoles() {
        return switch (this) {
            case MANAGER -> EnumSet.allOf(Role.class);
            case EDITING_TEACHER -> EnumSet.of(TEACHER, STUDENT);
            case TEACHER, STUDENT -> EnumSet.noneOf(Role.class);
        };
    }

    /** {@code true} for the roles Moodle counts as staff of a course (teacher, editing teacher, manager). */
    public boolean isStaff() {
        return this != STUDENT;
    }
}
