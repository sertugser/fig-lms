package com.figlms.course.api;

import java.util.Optional;

/** Look up courses by id or short name. */
public interface CourseCatalog {

    Optional<CourseSummary> findByShortname(String shortname);

    Optional<CourseSummary> findById(long id);

    /** @throws UnknownCourseException if no course has this short name */
    default CourseSummary require(String shortname) {
        return findByShortname(shortname).orElseThrow(() -> new UnknownCourseException(shortname));
    }
}
