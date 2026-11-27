package com.figlms.identity.internal;

import com.figlms.ApiException;
import com.figlms.identity.api.UnknownUserException;
import com.figlms.identity.api.UserAccount;
import java.time.Clock;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creating accounts, changing passwords, suspending (REQ-IDN-01, REQ-IDN-07). */
@Service
@Transactional
class AccountService {

    /** Moodle {@code PARAM_USERNAME}: lowercase letters, digits and {@code _ - @ .}; at most 100 characters. */
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9_.@-]{1,100}");

    private final AccountRepository accounts;
    private final PasswordPolicy policy;
    private final PasswordEncoder encoder;
    private final Clock clock;

    AccountService(AccountRepository accounts, PasswordPolicy policy, PasswordEncoder encoder, Clock clock) {
        this.accounts = accounts;
        this.policy = policy;
        this.encoder = encoder;
        this.clock = clock;
    }

    UserAccount create(String username, String password, String firstname, String lastname, String email) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw ApiException.invalid("INVALID_USERNAME",
                    "The username can only contain lowercase letters, digits, _, -, @ and .");
        }
        if (isBlank(firstname) || isBlank(lastname)) {
            throw ApiException.invalid("NAME_REQUIRED", "First name and last name are required.");
        }
        if (accounts.usernameExists(username)) {
            throw ApiException.conflict("USERNAME_TAKEN", "This username already exists, choose another");
        }
        requireValidPassword(password);
        long id = accounts.insert(username, encoder.encode(password), firstname.strip(), lastname.strip(),
                email == null ? "" : email.strip(), clock.instant());
        return accounts.findById(id).orElseThrow();
    }

    void changePassword(String username, String currentPassword, String newPassword) {
        AccountRepository.LoginRecord login = accounts.findLogin(username).orElseThrow(() -> new UnknownUserException(username));
        if (login.passwordHash() == null || !encoder.matches(currentPassword == null ? "" : currentPassword,
                login.passwordHash())) {
            throw ApiException.invalid("INVALID_CURRENT_PASSWORD", "Invalid current password");
        }
        requireValidPassword(newPassword);
        accounts.updatePassword(login.id(), encoder.encode(newPassword), clock.instant());
    }

    UserAccount setSuspended(String username, boolean suspended) {
        UserAccount account = accounts.findByUsername(username).orElseThrow(() -> new UnknownUserException(username));
        if (account.siteAdmin() && suspended) {
            throw ApiException.invalid("CANNOT_SUSPEND_ADMIN", "The site administrator cannot be suspended.");
        }
        accounts.setSuspended(account.id(), suspended);
        return accounts.findById(account.id()).orElseThrow();
    }

    private void requireValidPassword(String password) {
        List<String> errors = policy.check(password);
        if (!errors.isEmpty()) {
            throw ApiException.invalid("PASSWORD_POLICY", String.join(" ", errors));
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
