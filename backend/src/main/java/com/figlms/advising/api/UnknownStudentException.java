package com.figlms.advising.api;

import com.figlms.ApiException;
import org.springframework.http.HttpStatus;

/** No user has the given username. Rendered as 404 {@code UNKNOWN_STUDENT}. */
public class UnknownStudentException extends ApiException {

    public UnknownStudentException(String student) {
        super(HttpStatus.NOT_FOUND, "UNKNOWN_STUDENT", "Unknown student: " + student);
    }
}
