package com.figlms.grading.api;

/**
 * A grade item as other modules see it.
 *
 * @param source     owner of an activity item, for example {@code assignment:12}; {@code null} for manual items
 * @param gradePass  grade to pass, {@code null} if none (Moodle {@code gradepass} 0)
 */
public record GradeItemInfo(long id, long courseId, String name, String source, double gradeMin, double gradeMax,
        Double gradePass, boolean hidden, boolean locked) {
}
