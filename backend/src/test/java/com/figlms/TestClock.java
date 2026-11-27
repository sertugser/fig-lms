package com.figlms;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A clock tests can set, so date rules (submission window, lockout, enrolment periods) are checked at exact
 * moments. Import {@link Config} and call {@link #reset()} before each test, because the Spring context is shared.
 */
public class TestClock extends Clock {

    /** "Today" of the sample data: after the CS101 cut-off, before the course end. */
    public static final Instant DEFAULT = Instant.parse("2026-11-27T12:00:00Z");

    private volatile Instant now = DEFAULT;

    public void set(Instant instant) {
        this.now = instant;
    }

    public void set(String isoInstant) {
        set(Instant.parse(isoInstant));
    }

    public void reset() {
        set(DEFAULT);
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }
}
