package com.figlms;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Fig LMS modular monolith. Each direct subpackage is one application module (see ADR-0001 and ADR-0002).
 *
 * <p>Classes in this root package ({@link ApiException}, {@link ApiErrorHandler}) are shared infrastructure, not a
 * module: they hold no business rules and every module may use them.
 */
@SpringBootApplication
public class FigLmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(FigLmsApplication.class, args);
    }

    /** The one clock of the application. Every time-based rule asks it for "now", so tests can fix the time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
