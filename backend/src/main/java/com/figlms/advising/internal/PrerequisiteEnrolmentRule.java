package com.figlms.advising.internal;

import com.figlms.advising.api.PrerequisiteCheck;
import com.figlms.advising.api.PrerequisiteService;
import com.figlms.course.api.EnrolmentRule;
import com.figlms.identity.api.Role;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * REQ-ADV-09 in the enrolment flow: a student is enrolled only if every prerequisite of the course is completed,
 * whatever the enrolment method (manual or self). Staff roles are not checked. This is the one place the rule lives;
 * in Moodle it would have to go into each of the 13 enrolment plugins (MDL-22648).
 */
@Component
class PrerequisiteEnrolmentRule implements EnrolmentRule {

    private final PrerequisiteService prerequisites;

    PrerequisiteEnrolmentRule(PrerequisiteService prerequisites) {
        this.prerequisites = prerequisites;
    }

    @Override
    public Optional<Refusal> check(EnrolmentRequest request) {
        if (request.role() != Role.STUDENT) {
            return Optional.empty();
        }
        PrerequisiteCheck check = prerequisites.check(request.username(), request.courseShortname());
        if (check.allowed()) {
            return Optional.empty();
        }
        return Optional.of(new Refusal("PREREQUISITES_MISSING", "You cannot enrol in " + request.courseShortname()
                + " before completing: " + String.join(", ", check.missingPrerequisites())));
    }
}
