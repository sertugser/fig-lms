package com.figlms.course.api;

import java.time.Instant;

/**
 * A course as other modules see it.
 *
 * @param enddate    {@code null} = no end date (Moodle 0)
 * @param showGrades course setting "Show gradebook to students" (Moodle {@code showgrades}, default on, REQ-GRD-06)
 */
public record CourseSummary(long id, String shortname, String fullname, String idnumber, long categoryId,
        Instant startdate, Instant enddate, boolean visible, boolean showGrades) {
}
