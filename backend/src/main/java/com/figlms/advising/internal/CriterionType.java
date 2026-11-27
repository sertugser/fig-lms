package com.figlms.advising.internal;

/**
 * Course completion criterion types (REQ-ADV-05). Moodle's "unenrol" and "duration" criteria are not implemented.
 * ACTIVITY, COURSE and ROLE criteria are first aggregated within their type, the others count one by one.
 */
enum CriterionType {
    SELF, DATE, ACTIVITY, GRADE, ROLE, COURSE;

    boolean aggregatedWithinType() {
        return this == ACTIVITY || this == COURSE || this == ROLE;
    }
}
