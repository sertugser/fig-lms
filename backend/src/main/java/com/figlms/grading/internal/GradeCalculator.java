package com.figlms.grading.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes category and course totals for one user, the way Moodle's {@code grade_category::aggregate_grades()} does
 * (REQ-GRD-01..04):
 *
 * <ol>
 *   <li>Every child (grade item or sub-category total) is scaled to 0..1 with its minimum and maximum.</li>
 *   <li>Empty grades are left out with "exclude empty grades", otherwise they count as 0. If every child is empty
 *       the total is empty.</li>
 *   <li>Drop lowest / keep highest remove children by their scaled value.</li>
 *   <li>The aggregation method combines the rest; the result is scaled to the total item's range.</li>
 * </ol>
 *
 * <p>Pure function without database access, so the rules are tested directly (GradeCalculatorTest).
 */
final class GradeCalculator {

    record Category(long id, Long parentId, Aggregation aggregation, boolean aggregateOnlyGraded, int dropLow,
            int keepHigh, long totalItemId) {
    }

    /** {@code categoryId}: the category the item is in, or for a total item, the category it totals. */
    record Item(long id, long categoryId, boolean total, double gradeMin, double gradeMax, double weight) {
    }

    private record Value(double scaled, double weight) {
    }

    private final Map<Long, Category> categories = new HashMap<>();
    private final Map<Long, Item> items = new HashMap<>();
    private final Map<Long, Double> grades;
    private final Map<Long, Double> totals = new HashMap<>();

    private GradeCalculator(List<Category> categories, List<Item> items, Map<Long, Double> grades) {
        categories.forEach(c -> this.categories.put(c.id(), c));
        items.forEach(i -> this.items.put(i.id(), i));
        this.grades = grades;
    }

    /**
     * @param grades final grade per item id for the user; only non-total items are read
     * @return total per total-item id; a missing key or {@code null} value means the total is empty
     */
    static Map<Long, Double> totals(List<Category> categories, List<Item> items, Map<Long, Double> grades) {
        GradeCalculator calculator = new GradeCalculator(categories, items, grades);
        for (Category c : categories) {
            calculator.total(c);
        }
        return calculator.totals;
    }

    private Double total(Category category) {
        if (totals.containsKey(category.totalItemId())) {
            return totals.get(category.totalItemId());
        }
        Item totalItem = items.get(category.totalItemId());
        List<Value> values = new ArrayList<>();
        boolean anyGraded = false;
        for (Item item : items.values()) {
            if (!item.total() && item.categoryId() == category.id()) {
                Double grade = grades.get(item.id());
                anyGraded |= grade != null;
                add(values, grade, item, category);
            }
        }
        for (Category child : categories.values()) {
            if (child.parentId() != null && child.parentId() == category.id()) {
                Double grade = total(child);
                anyGraded |= grade != null;
                add(values, grade, items.get(child.totalItemId()), category);
            }
        }
        Double result = null;
        if (anyGraded) {
            List<Value> kept = applyLimitRules(values, category);
            Double scaled = aggregate(kept, category.aggregation());
            if (scaled != null) {
                double value = totalItem.gradeMin() + scaled * (totalItem.gradeMax() - totalItem.gradeMin());
                result = Math.max(totalItem.gradeMin(), Math.min(totalItem.gradeMax(), value));
            }
        }
        totals.put(category.totalItemId(), result);
        return result;
    }

    private static void add(List<Value> values, Double grade, Item item, Category category) {
        double range = item.gradeMax() - item.gradeMin();
        if (range <= 0) {
            return;
        }
        if (grade == null) {
            if (!category.aggregateOnlyGraded()) {
                values.add(new Value(0, item.weight()));
            }
            return;
        }
        values.add(new Value((grade - item.gradeMin()) / range, item.weight()));
    }

    /** REQ-GRD-04. Drop lowest wins over keep highest (the service resets keep highest when both are set). */
    private static List<Value> applyLimitRules(List<Value> values, Category category) {
        List<Value> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.comparingDouble(Value::scaled));
        if (category.dropLow() > 0) {
            return sorted.size() <= category.dropLow() ? List.of() : sorted.subList(category.dropLow(), sorted.size());
        }
        if (category.keepHigh() > 0 && sorted.size() > category.keepHigh()) {
            return sorted.subList(sorted.size() - category.keepHigh(), sorted.size());
        }
        return sorted;
    }

    private static Double aggregate(List<Value> values, Aggregation aggregation) {
        if (values.isEmpty()) {
            return null;
        }
        return switch (aggregation) {
            case MEAN -> values.stream().mapToDouble(Value::scaled).average().orElseThrow();
            case WEIGHTED_MEAN -> {
                // REQ-GRD-02: weight <= 0 is ignored; if the weights add up to 0 the total is empty.
                double weightSum = 0;
                double sum = 0;
                for (Value v : values) {
                    if (v.weight() > 0) {
                        weightSum += v.weight();
                        sum += v.weight() * v.scaled();
                    }
                }
                yield weightSum == 0 ? null : sum / weightSum;
            }
            case MEDIAN -> {
                double[] sorted = values.stream().mapToDouble(Value::scaled).sorted().toArray();
                int mid = sorted.length / 2;
                yield sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
            }
            case MIN -> values.stream().mapToDouble(Value::scaled).min().orElseThrow();
            case MAX -> values.stream().mapToDouble(Value::scaled).max().orElseThrow();
        };
    }
}
