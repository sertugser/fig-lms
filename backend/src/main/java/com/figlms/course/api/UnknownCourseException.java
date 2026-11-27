package com.figlms.course.api;

import com.figlms.ApiException;
import org.springframework.http.HttpStatus;

/** No course has the given short name (or id). Rendered as 404 {@code UNKNOWN_COURSE}. */
public class UnknownCourseException extends ApiException {

    public UnknownCourseException(String course) {
        super(HttpStatus.NOT_FOUND, "UNKNOWN_COURSE", "Unknown course: " + course);
    }
}
