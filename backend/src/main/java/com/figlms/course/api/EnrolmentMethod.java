package com.figlms.course.api;

/** Enrolment methods in scope (Moodle enrol plugins). Guest is created disabled, for parity with Moodle (REQ-CRS-03). */
public enum EnrolmentMethod {
    MANUAL, SELF, GUEST
}
