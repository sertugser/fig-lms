package com.figlms.grading.api;

import java.util.Optional;

/**
 * The gradebook as activities and other modules use it (Moodle {@code grade_update()} and the grade API).
 *
 * <p>Activities own their grading and push the result here, as {@code mod/assign} does with {@code grade_update()};
 * the gradebook never calls back into an activity.
 */
public interface GradebookService {

    /** Creates the grade item of an activity in the course's top category; returns its id. */
    long createActivityItem(long courseId, String source, String name, double gradeMin, double gradeMax, Double gradePass);

    void updateActivityItem(long itemId, String name, double gradeMax, Double gradePass);

    /**
     * Stores the grade of one user (REQ-GRD-05: limited to the item's minimum and maximum). {@code null} removes it.
     *
     * @throws com.figlms.ApiException 409 {@code GRADE_LOCKED} if the item or the grade is locked
     */
    void pushGrade(long itemId, long userId, Double rawGrade, long graderUserId);

    Optional<GradeItemInfo> item(long itemId);

    /** The stored (final) grade, empty if the user has none. */
    Optional<Double> grade(long itemId, long userId);

    /** The grade as a percentage 0..100 of the item's range (REQ-ADV-04 grade condition); empty without a grade. */
    Optional<Double> gradePercent(long itemId, long userId);

    /** Item or grade locked (REQ-ASG-03: blocks editing the submission). */
    boolean isLocked(long itemId, long userId);

    /** The course total of the user in the course total's range (default 0..100), empty if it has no grade. */
    Optional<Double> courseTotal(long courseId, long userId);
}
