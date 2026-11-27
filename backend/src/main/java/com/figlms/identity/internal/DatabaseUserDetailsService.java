package com.figlms.identity.internal;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads accounts for Spring Security (REQ-IDN-01). Suspended accounts and accounts without a password are disabled,
 * locked accounts are locked; Spring then refuses the login before it compares the password, like Moodle's
 * {@code authenticate_user_login()}. Authorities: {@code ROLE_USER} for everyone, {@code ROLE_ADMIN} for the site
 * administrator. Course roles are not authorities: they depend on the course (course module, {@code AccessControl}).
 */
@Service
class DatabaseUserDetailsService implements UserDetailsService {

    private final AccountRepository accounts;
    private final LoginThrottle throttle;

    DatabaseUserDetailsService(AccountRepository accounts, LoginThrottle throttle) {
        this.accounts = accounts;
        this.throttle = throttle;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        AccountRepository.LoginRecord login = accounts.findLogin(username)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid login"));
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (login.siteAdmin()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        boolean hasPassword = login.passwordHash() != null && !login.passwordHash().isBlank();
        return User.withUsername(login.username())
                // A disabled account still needs a non-null password for the builder; it is never compared.
                .password(hasPassword ? login.passwordHash() : "{noop}")
                .disabled(login.suspended() || !hasPassword)
                .accountLocked(throttle.isLocked(login))
                .authorities(authorities)
                .build();
    }
}
