package com.figlms.advising.api;

/**
 * Access restrictions on items inside a course (REQ-ADV-01..07), stored as JSON in Moodle's format:
 *
 * <pre>
 * {"op":"&amp;", "showc":[true, false], "c":[
 *     {"type":"completion", "cm":"assignment:3", "e":1},
 *     {"type":"date", "d":"&gt;=", "t":1767225600},
 *     {"type":"grade", "id":42, "min":72.0}]}
 * </pre>
 *
 * Operators {@code &}, {@code |}, {@code !&}, {@code !|}; trees can be nested. The root of an {@code &} or {@code !|}
 * tree has one {@code showc} flag per child, the root of a {@code |} or {@code !&} tree one {@code show} flag.
 * {@code cm} is an activity key registered through {@link CompletionService}; {@code e} is 0 incomplete, 1 complete,
 * 2 complete and passed, 3 complete and failed; {@code t} is in epoch seconds; {@code id} is a grade item id and
 * {@code min} / {@code max} are percentages.
 */
public interface AvailabilityService {

    /**
     * @param restriction JSON as above; {@code null} or blank = no restriction
     * @throws com.figlms.ApiException 400 {@code INVALID_AVAILABILITY} for a malformed tree
     */
    void validate(String restriction);

    /**
     * Evaluates the restriction for the user. Users with {@code moodle/course:ignoreavailabilityrestrictions} in the
     * course always get {@code available = true} (REQ-ADV-07); the information text still says what applies.
     */
    Availability evaluate(String restriction, long courseId, String username);
}
