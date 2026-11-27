package com.figlms.assignment.internal;

/**
 * When the assignment counts as complete for a student (Moodle {@code completionsubmit} and
 * {@code completionusegrade}).
 */
enum CompletionRule {
    /** No completion tracking. */
    NONE,
    /** Complete once the work is submitted for grading. */
    SUBMIT,
    /** Complete once graded: passed or failed when the assignment has a grade to pass, otherwise just complete. */
    GRADE
}
