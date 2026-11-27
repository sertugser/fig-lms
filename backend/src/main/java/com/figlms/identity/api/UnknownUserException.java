package com.figlms.identity.api;

import com.figlms.ApiException;
import org.springframework.http.HttpStatus;

/** No account has the given username. Rendered as 404 {@code UNKNOWN_USER}. */
public class UnknownUserException extends ApiException {

    public UnknownUserException(String username) {
        super(HttpStatus.NOT_FOUND, "UNKNOWN_USER", "Unknown user: " + username);
    }
}
