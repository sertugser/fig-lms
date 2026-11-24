package com.figlms.advising.api;

/** No course has the given short name. */
public class UnknownCourseException extends RuntimeException {

    public UnknownCourseException(String course) {
        super("Unknown course: " + course);
    }
}
