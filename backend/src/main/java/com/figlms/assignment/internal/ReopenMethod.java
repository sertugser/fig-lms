package com.figlms.assignment.internal;

/** Moodle {@code attemptreopenmethod} (REQ-ASG-07). The site default is {@link #UNTIL_PASS}, as in Moodle 5.3. */
enum ReopenMethod {
    /** The grader asks for another attempt ("Allow another attempt"). */
    MANUAL,
    /** Every grade on the latest attempt opens another one. */
    AUTOMATIC,
    /** Another attempt opens only when the gradebook says the grade fails the grade to pass. */
    UNTIL_PASS
}
