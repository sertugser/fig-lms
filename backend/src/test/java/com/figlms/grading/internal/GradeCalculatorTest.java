package com.figlms.grading.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * REQ-GRD-01..04 on the pure calculator. One category (id 1) with the course total (item 100, 0..100) and three
 * activity items: 1 (0..100), 2 (0..50), 3 (0..10).
 */
class GradeCalculatorTest {

    static final long TOTAL = 100;

    static List<GradeCalculator.Item> items(double w1, double w2, double w3) {
        return List.of(new GradeCalculator.Item(TOTAL, 1, true, 0, 100, 1),
                new GradeCalculator.Item(1, 1, false, 0, 100, w1),
                new GradeCalculator.Item(2, 1, false, 0, 50, w2),
                new GradeCalculator.Item(3, 1, false, 0, 10, w3));
    }

    static Double total(Aggregation aggregation, boolean onlyGraded, int dropLow, int keepHigh, Map<Long, Double> grades) {
        return total(aggregation, onlyGraded, dropLow, keepHigh, grades, items(1, 1, 1));
    }

    static Double total(Aggregation aggregation, boolean onlyGraded, int dropLow, int keepHigh, Map<Long, Double> grades,
            List<GradeCalculator.Item> items) {
        GradeCalculator.Category root = new GradeCalculator.Category(1, null, aggregation, onlyGraded, dropLow, keepHigh, TOTAL);
        return GradeCalculator.totals(List.of(root), items, grades).get(TOTAL);
    }

    static Map<Long, Double> grades(Double g1, Double g2, Double g3) {
        Map<Long, Double> grades = new HashMap<>();
        grades.put(1L, g1);
        grades.put(2L, g2);
        grades.put(3L, g3);
        return grades;
    }

    @Test
    void normal_meanOfScaledGrades() {
        // 80/100 = 0.8, 25/50 = 0.5, 5/10 = 0.5 -> 0.6 -> 60 on 0..100
        assertThat(total(Aggregation.MEAN, true, 0, 0, grades(80.0, 25.0, 5.0))).isCloseTo(60.0, within(1e-9));
    }

    @Test
    void normal_weightedMean() {
        // weights 2, 1, 1: (0.8*2 + 0.5 + 0.5) / 4 = 0.65
        assertThat(total(Aggregation.WEIGHTED_MEAN, true, 0, 0, grades(80.0, 25.0, 5.0), items(2, 1, 1)))
                .isCloseTo(65.0, within(1e-9));
    }

    @Test
    void normal_medianMinMax() {
        Map<Long, Double> g = grades(80.0, 10.0, 7.0); // 0.8, 0.2, 0.7
        assertThat(total(Aggregation.MEDIAN, true, 0, 0, g)).isCloseTo(70.0, within(1e-9));
        assertThat(total(Aggregation.MIN, true, 0, 0, g)).isCloseTo(20.0, within(1e-9));
        assertThat(total(Aggregation.MAX, true, 0, 0, g)).isCloseTo(80.0, within(1e-9));
    }

    @Test
    void boundary_emptyGradesAreExcludedWhenOnlyGraded() {
        assertThat(total(Aggregation.MEAN, true, 0, 0, grades(80.0, null, null))).isCloseTo(80.0, within(1e-9));
    }

    @Test
    void boundary_emptyGradesCountAsZeroOtherwise() {
        assertThat(total(Aggregation.MEAN, false, 0, 0, grades(90.0, null, null))).isCloseTo(30.0, within(1e-9));
    }

    @Test
    void boundary_noGradesGiveNoTotal() {
        assertThat(total(Aggregation.MEAN, true, 0, 0, grades(null, null, null))).isNull();
    }

    @Test
    void normal_dropLowest() {
        // 0.8, 0.2, 0.7; drop 1 -> mean(0.8, 0.7) = 0.75
        assertThat(total(Aggregation.MEAN, true, 1, 0, grades(80.0, 10.0, 7.0))).isCloseTo(75.0, within(1e-9));
    }

    @Test
    void normal_keepHighest() {
        // keep 1 -> 0.8
        assertThat(total(Aggregation.MEAN, true, 0, 1, grades(80.0, 10.0, 7.0))).isCloseTo(80.0, within(1e-9));
    }

    @Test
    void boundary_droppingEveryGradeLeavesNoTotal() {
        // Moodle apply_limit_rules() stops when nothing is left to drop; aggregate_grades() then stores no total.
        assertThat(total(Aggregation.MEAN, true, 5, 0, grades(80.0, 10.0, 7.0))).isNull();
    }

    @Test
    void normal_subCategoryTotalIsScaledIntoTheParent() {
        List<GradeCalculator.Category> categories = new ArrayList<>(List.of(
                new GradeCalculator.Category(1, null, Aggregation.MEAN, true, 0, 0, TOTAL),
                new GradeCalculator.Category(2, 1L, Aggregation.MEAN, true, 0, 0, 200)));
        List<GradeCalculator.Item> items = List.of(new GradeCalculator.Item(TOTAL, 1, true, 0, 100, 1),
                new GradeCalculator.Item(200, 2, true, 0, 100, 1),
                new GradeCalculator.Item(1, 1, false, 0, 100, 1),
                new GradeCalculator.Item(2, 2, false, 0, 100, 1),
                new GradeCalculator.Item(3, 2, false, 0, 100, 1));
        // sub-category: mean(0.4, 0.6) = 0.5; course: mean(1.0, 0.5) = 0.75
        Map<Long, Double> totals = GradeCalculator.totals(categories, items, Map.of(1L, 100.0, 2L, 40.0, 3L, 60.0));
        assertThat(totals.get(200L)).isCloseTo(50.0, within(1e-9));
        assertThat(totals.get(TOTAL)).isCloseTo(75.0, within(1e-9));
    }
}
