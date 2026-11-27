package com.figlms.identity.api;

import java.util.Optional;

/** Look up user accounts. The only way other modules learn about users. */
public interface UserDirectory {

    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findById(long id);

    /**
     * @throws UnknownUserException if no account has this username
     */
    default UserAccount require(String username) {
        return findByUsername(username).orElseThrow(() -> new UnknownUserException(username));
    }
}
