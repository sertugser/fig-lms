package com.figlms.course.api;

import com.figlms.identity.api.Role;
import java.util.Optional;

/**
 * Extension point: another module may refuse an enrolment. The course module calls every {@code EnrolmentRule} bean
 * before it enrols anyone, whatever the enrolment method.
 *
 * <p>This replaces Moodle's situation where each of the 13 enrolment plugins decides alone, so a rule such as course
 * prerequisites (MDL-22648) would have to be added to every plugin. Here the advising module implements this
 * interface once (see ADR-0002). The course module does not depend on the modules that implement it.
 */
public interface EnrolmentRule {

    /** What is about to happen. */
    record EnrolmentRequest(long userId, String username, long courseId, String courseShortname, Role role,
            EnrolmentMethod method) {
    }

    /**
     * Why the enrolment is refused. Rendered as 409 with {@code code} and {@code message}.
     */
    record Refusal(String code, String message) {
    }

    /** @return a refusal, or empty to allow the enrolment */
    Optional<Refusal> check(EnrolmentRequest request);
}
