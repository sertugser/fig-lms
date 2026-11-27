package com.figlms.grading.internal;

/**
 * Aggregation methods of a grade category (REQ-GRD-01). Moodle's other methods (mode, simple weighted mean, mean with
 * extra credits, natural) are not implemented; see grading.md.
 */
enum Aggregation {
    MEAN, WEIGHTED_MEAN, MEDIAN, MIN, MAX
}
