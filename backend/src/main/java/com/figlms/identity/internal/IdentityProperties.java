package com.figlms.identity.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Security settings of the identity module ({@code fig.identity.*}). Defaults are the targets of the KPI table
 * ({@code docs/srs/kpi.md}): SEC-08 lockout, SEC-09 password length, SEC-10 session timeout. The Moodle 5.3.1
 * defaults are in the comments, so a golden master run can switch to them.
 */
@ConfigurationProperties("fig.identity")
record IdentityProperties(@DefaultValue Lockout lockout, @DefaultValue Password password,
        @DefaultValue("2h") Duration sessionTimeout) {

    /**
     * Failed-login lockout (REQ-IDN-06). {@code threshold = 0} switches lockout off, as in Moodle
     * ({@code lockoutthreshold}, default 0; window and duration default 30 min).
     */
    record Lockout(@DefaultValue("5") int threshold, @DefaultValue("15m") Duration window,
            @DefaultValue("15m") Duration duration) {
    }

    /**
     * Password policy (REQ-IDN-07). Moodle default: 8 characters, at least one digit, lowercase, uppercase and
     * non-alphanumeric character. The target raises the length to 12 (SEC-09).
     */
    record Password(@DefaultValue("12") int minLength, @DefaultValue("1") int minDigits,
            @DefaultValue("1") int minLower, @DefaultValue("1") int minUpper, @DefaultValue("1") int minNonAlphanumeric) {
    }
}
