package com.figlms.identity.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Gives the sample accounts of the Flyway seed (student1..5, teacher1, teacher2, admin; {@code sample_account = TRUE})
 * the shared password {@code fig.sample-users.password} ({@code SAMPLE_USER_PASSWORD}) at startup.
 *
 * <p>The seed stores no password, so the accounts cannot log in until this runs; with an empty setting they stay
 * without password, which is what production wants. The sample password is not checked against the password policy,
 * the same way Moodle's test data generator skips it.
 */
@Component
class SampleAccounts implements ApplicationRunner {

    private final AccountRepository accounts;
    private final PasswordEncoder encoder;
    private final String password;

    SampleAccounts(AccountRepository accounts, PasswordEncoder encoder,
            @Value("${fig.sample-users.password:}") String password) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        accounts.setSamplePasswordHash(password.isBlank() ? null : encoder.encode(password));
    }
}
