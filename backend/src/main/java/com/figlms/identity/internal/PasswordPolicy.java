package com.figlms.identity.internal;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Moodle's password policy (REQ-IDN-07, {@code check_password_policy()} in {@code lib/moodlelib.php}): minimum length
 * and minimum numbers of digits, lowercase, uppercase and non-alphanumeric characters. The messages are Moodle's
 * ({@code lang/en/moodle.php} {@code errorminpassword*}).
 */
@Component
class PasswordPolicy {

    private final IdentityProperties.Password rules;

    PasswordPolicy(IdentityProperties properties) {
        this.rules = properties.password();
    }

    /** @return the broken rules, empty if the password is acceptable */
    List<String> check(String password) {
        List<String> errors = new ArrayList<>();
        String p = password == null ? "" : password;
        if (p.codePointCount(0, p.length()) < rules.minLength()) {
            errors.add("Passwords must be at least " + rules.minLength() + " characters long.");
        }
        if (count(p, Character::isDigit) < rules.minDigits()) {
            errors.add("Passwords must have at least " + rules.minDigits() + " digit(s).");
        }
        if (count(p, Character::isLowerCase) < rules.minLower()) {
            errors.add("Passwords must have at least " + rules.minLower() + " lower case letter(s).");
        }
        if (count(p, Character::isUpperCase) < rules.minUpper()) {
            errors.add("Passwords must have at least " + rules.minUpper() + " upper case letter(s).");
        }
        if (count(p, c -> !Character.isLetterOrDigit(c)) < rules.minNonAlphanumeric()) {
            errors.add("The password must have at least " + rules.minNonAlphanumeric()
                    + " special character(s) such as as *, -, or #.");
        }
        return errors;
    }

    IdentityProperties.Password rules() {
        return rules;
    }

    private static long count(String s, java.util.function.IntPredicate kind) {
        return s.codePoints().filter(kind).count();
    }
}
