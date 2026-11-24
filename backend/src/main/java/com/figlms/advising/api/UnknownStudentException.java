package com.figlms.advising.api;

/** No student has the given username. */
public class UnknownStudentException extends RuntimeException {

    public UnknownStudentException(String student) {
        super("Unknown student: " + student);
    }
}
