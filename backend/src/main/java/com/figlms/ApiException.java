package com.figlms;

import org.springframework.http.HttpStatus;

/**
 * A rule refused the request. Rendered by {@link ApiErrorHandler} as {@code {"error": code, "message": message}}
 * with the given HTTP status.
 *
 * <p>The code is stable and meant for tests and clients (for example {@code SHORTNAME_TAKEN}); the message is for
 * people and follows the Moodle wording where a requirement quotes it.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    /** 400: the input breaks a validation rule. */
    public static ApiException invalid(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    /** 404: the thing does not exist, or the caller may not know that it exists. */
    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    /** 403: the caller is logged in but lacks the capability. */
    public static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }

    /** 409: the request is valid but conflicts with the current state (for example a duplicate). */
    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
