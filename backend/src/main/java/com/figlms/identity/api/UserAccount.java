package com.figlms.identity.api;

/**
 * A user account as other modules see it. The password hash never leaves the identity module.
 *
 * @param id        stable id, used by other modules to refer to the user
 * @param username  login name, lowercase (Moodle {@code PARAM_USERNAME})
 * @param siteAdmin the site administrator flag: every capability in every course (Moodle site admin)
 */
public record UserAccount(long id, String username, String firstname, String lastname, String email,
        boolean suspended, boolean siteAdmin) {

    public String fullName() {
        return firstname + " " + lastname;
    }
}
