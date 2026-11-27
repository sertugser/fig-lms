package com.figlms.assignment.internal;

import java.time.Instant;

/**
 * The dates that apply to one student (REQ-ASG-01, REQ-ASG-02): the assignment's dates with the student's override
 * applied, plus the student's extension. Pure logic, as Moodle {@code update_effective_access()},
 * {@code submissions_open()} and the lateness check of the status page.
 *
 * @param allowSubmissionsFrom {@code null} = open from the start
 * @param cutoffDate           {@code null} = no cut-off
 * @param extensionDueDate     {@code null} = no extension
 */
record Window(Instant allowSubmissionsFrom, Instant dueDate, Instant cutoffDate, Instant extensionDueDate,
        boolean locked) {

    static Window of(AssignmentRepository.AssignmentRow a, AssignmentRepository.OverrideRow o,
            AssignmentRepository.FlagRow flags) {
        Instant from = a.allowSubmissionsFrom();
        Instant due = a.dueDate();
        Instant cutoff = a.cutoffDate();
        if (o != null) {
            // Moodle applies each overridden field on its own; a field the override leaves empty keeps the
            // assignment's value.
            from = o.allowSubmissionsFrom() != null ? o.allowSubmissionsFrom() : from;
            due = o.dueDate() != null ? o.dueDate() : due;
            cutoff = o.cutoffDate() != null ? o.cutoffDate() : cutoff;
        }
        return new Window(from, due, cutoff, flags.extensionDueDate(), flags.locked());
    }

    /**
     * Last moment a submission is accepted: the cut-off, moved later by a later extension. An earlier extension
     * leaves the cut-off in place; without a cut-off there is no last moment ({@code null}) and an extension changes
     * nothing (Moodle only looks at the extension when there is a cut-off).
     */
    Instant finalDate() {
        if (cutoffDate == null) {
            return null;
        }
        return extensionDueDate != null && extensionDueDate.isAfter(cutoffDate) ? extensionDueDate : cutoffDate;
    }

    /** REQ-ASG-01: open from the opening date through the final date, both inclusive, and not locked. */
    boolean isOpen(Instant now) {
        if (locked) {
            return false;
        }
        if (allowSubmissionsFrom != null && now.isBefore(allowSubmissionsFrom)) {
            return false;
        }
        Instant last = finalDate();
        return last == null || !now.isAfter(last);
    }

    boolean notYetOpen(Instant now) {
        return allowSubmissionsFrom != null && now.isBefore(allowSubmissionsFrom);
    }

    /** The due date that lateness is measured against: the due date, or a later extension. */
    Instant effectiveDueDate() {
        if (dueDate == null) {
            return null;
        }
        return extensionDueDate != null && extensionDueDate.isAfter(dueDate) ? extensionDueDate : dueDate;
    }

    /** REQ-ASG-02: work is late when it was last changed strictly after the effective due date. */
    boolean isLate(Instant lastModified) {
        Instant due = effectiveDueDate();
        return due != null && lastModified != null && lastModified.isAfter(due);
    }
}
