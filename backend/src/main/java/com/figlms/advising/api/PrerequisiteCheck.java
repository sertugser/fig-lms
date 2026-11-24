package com.figlms.advising.api;

import java.util.List;

/**
 * Result of {@link PrerequisiteService#check(String, String)}. This record is also the JSON body of
 * {@code GET /api/advising/prerequisite-check}.
 *
 * @param student              username of the student
 * @param course               short name of the course the student wants to enrol in
 * @param allowed              {@code true} if every prerequisite is completed (or the course has none)
 * @param reason               why the student is or is not allowed
 * @param missingPrerequisites short names of the prerequisites the student has not completed, sorted; empty if allowed
 */
public record PrerequisiteCheck(String student, String course, boolean allowed, Reason reason,
        List<String> missingPrerequisites) {

    public PrerequisiteCheck {
        missingPrerequisites = List.copyOf(missingPrerequisites);
    }

    public enum Reason {
        /** The course has no prerequisite. */
        NO_PREREQUISITES,
        /** The student has completed every prerequisite. */
        PREREQUISITES_COMPLETED,
        /** At least one prerequisite is not completed; see {@code missingPrerequisites}. */
        PREREQUISITES_MISSING
    }
}
