package com.figlms.identity.internal;

import java.time.Clock;
import java.time.Instant;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Failed-login lockout (REQ-IDN-06), the same algorithm as Moodle's {@code login_attempt_failed()} in
 * {@code lib/authlib.php}: a failure older than the window resets the counter; when the counter reaches the threshold
 * the account is locked for the configured duration; a successful login resets everything. Threshold 0 = off.
 *
 * <p>Only failures of existing accounts are counted, so unknown usernames cannot be used to fill the table.
 */
@Component
class LoginThrottle {

    private final AccountRepository accounts;
    private final IdentityProperties.Lockout rules;
    private final Clock clock;

    LoginThrottle(AccountRepository accounts, IdentityProperties properties, Clock clock) {
        this.accounts = accounts;
        this.rules = properties.lockout();
        this.clock = clock;
    }

    boolean isLocked(AccountRepository.LoginRecord login) {
        return rules.threshold() > 0 && login.lockedUntil() != null && clock.instant().isBefore(login.lockedUntil());
    }

    @EventListener
    @Transactional
    void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        if (rules.threshold() <= 0) {
            return;
        }
        accounts.findLogin(event.getAuthentication().getName()).ifPresent(login -> {
            Instant now = clock.instant();
            boolean windowExpired = login.lastFailedAt() == null
                    || !now.isBefore(login.lastFailedAt().plus(rules.window()));
            int count = (windowExpired ? 0 : login.failedCount()) + 1;
            Instant lockedUntil = count >= rules.threshold() ? now.plus(rules.duration()) : login.lockedUntil();
            accounts.recordFailure(login.id(), count, now, lockedUntil);
        });
    }

    @EventListener
    @Transactional
    void onSuccess(AuthenticationSuccessEvent event) {
        accounts.findLogin(event.getAuthentication().getName()).ifPresent(login -> {
            if (login.failedCount() > 0 || login.lockedUntil() != null) {
                accounts.resetFailures(login.id());
            }
        });
    }
}
