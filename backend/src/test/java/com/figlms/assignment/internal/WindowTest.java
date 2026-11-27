package com.figlms.assignment.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** REQ-ASG-01 and REQ-ASG-02 on the pure date logic. */
class WindowTest {

    static final Instant FROM = Instant.parse("2026-10-01T00:00:00Z");
    static final Instant DUE = Instant.parse("2026-11-15T23:59:00Z");
    static final Instant CUTOFF = Instant.parse("2026-11-22T23:59:00Z");

    static AssignmentRepository.AssignmentRow assignment(Instant from, Instant due, Instant cutoff) {
        return new AssignmentRepository.AssignmentRow(1, 1, "A", "", from, due, cutoff, false, false, 100, null,
                ReopenMethod.UNTIL_PASS, 1, false, true, 1, 0, List.of(), CompletionRule.NONE, null, null);
    }

    static Window window(Instant from, Instant due, Instant cutoff, Instant extension, boolean locked) {
        return Window.of(assignment(from, due, cutoff), null, new AssignmentRepository.FlagRow(locked, extension));
    }

    @Test
    void boundary_opensAtTheOpeningDateAndClosesAfterTheCutoff() {
        Window w = window(FROM, DUE, CUTOFF, null, false);
        assertThat(w.isOpen(FROM.minusSeconds(1))).isFalse();
        assertThat(w.isOpen(FROM)).isTrue();
        assertThat(w.isOpen(CUTOFF)).isTrue();
        assertThat(w.isOpen(CUTOFF.plusSeconds(1))).isFalse();
    }

    @Test
    void boundary_laterExtensionMovesTheCutoff() {
        Instant extension = CUTOFF.plusSeconds(86_400);
        Window w = window(FROM, DUE, CUTOFF, extension, false);
        assertThat(w.isOpen(CUTOFF.plusSeconds(1))).isTrue();
        assertThat(w.isOpen(extension)).isTrue();
        assertThat(w.isOpen(extension.plusSeconds(1))).isFalse();
    }

    @Test
    void boundary_earlierExtensionLeavesTheCutoff() {
        Window w = window(FROM, DUE, CUTOFF, DUE.plusSeconds(60), false);
        assertThat(w.isOpen(CUTOFF)).isTrue();
        assertThat(w.finalDate()).isEqualTo(CUTOFF);
    }

    @Test
    void normal_withoutCutoffTheDueDateDoesNotClose() {
        Window w = window(FROM, DUE, null, null, false);
        assertThat(w.isOpen(DUE.plusSeconds(365L * 86_400))).isTrue();
    }

    @Test
    void invalid_lockedIsNeverOpen() {
        assertThat(window(FROM, DUE, CUTOFF, null, true).isOpen(DUE)).isFalse();
    }

    @Test
    void boundary_lateOnlyStrictlyAfterTheDueDate() {
        Window w = window(FROM, DUE, CUTOFF, null, false);
        assertThat(w.isLate(DUE)).isFalse();
        assertThat(w.isLate(DUE.plusSeconds(1))).isTrue();
    }

    @Test
    void boundary_laterExtensionMakesTheSameTimeOnTime() {
        Window w = window(FROM, DUE, CUTOFF, DUE.plusSeconds(3600), false);
        assertThat(w.isLate(DUE.plusSeconds(1))).isFalse();
    }

    @Test
    void normal_overrideReplacesOnlyTheFieldsItSets() {
        Instant later = DUE.plusSeconds(86_400);
        Window w = Window.of(assignment(FROM, DUE, CUTOFF), new AssignmentRepository.OverrideRow(null, later, null),
                AssignmentRepository.FlagRow.NONE);
        assertThat(w.allowSubmissionsFrom()).isEqualTo(FROM);
        assertThat(w.dueDate()).isEqualTo(later);
        assertThat(w.cutoffDate()).isEqualTo(CUTOFF);
    }
}
