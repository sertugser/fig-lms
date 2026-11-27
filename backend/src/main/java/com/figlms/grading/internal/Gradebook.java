package com.figlms.grading.internal;

import com.figlms.ApiException;
import com.figlms.grading.api.GradeItemInfo;
import com.figlms.grading.api.GradebookService;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The gradebook: items, grades and totals of a course. Implements the module's API for activities. */
@Service
@Transactional
@EnableConfigurationProperties(GradingProperties.class)
class Gradebook implements GradebookService {

    private final GradingRepository repository;
    private final GradingProperties properties;
    private final Clock clock;

    Gradebook(GradingRepository repository, GradingProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public long createActivityItem(long courseId, String source, String name, double gradeMin, double gradeMax,
            Double gradePass) {
        GradingRepository.CategoryRow root = ensureRoot(courseId);
        validateRange(gradeMin, gradeMax, gradePass);
        return repository.insertItem(courseId, root.id(), name, "ACTIVITY", source, gradeMin, gradeMax, gradePass, 1.0);
    }

    @Override
    public void updateActivityItem(long itemId, String name, double gradeMax, Double gradePass) {
        GradingRepository.ItemRow item = requireItem(itemId);
        validateRange(item.gradeMin(), gradeMax, gradePass);
        repository.updateItem(itemId, name, gradeMax, gradePass, item.weight(), item.hidden(), item.locked());
    }

    @Override
    public void pushGrade(long itemId, long userId, Double rawGrade, long graderUserId) {
        GradingRepository.ItemRow item = requireItem(itemId);
        if (isLocked(itemId, userId)) {
            throw ApiException.conflict("GRADE_LOCKED", "The grade is locked and cannot be changed.");
        }
        repository.upsertGrade(itemId, userId, rawGrade, bounded(item, rawGrade), graderUserId, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GradeItemInfo> item(long itemId) {
        return repository.item(itemId).map(Gradebook::info);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Double> grade(long itemId, long userId) {
        return repository.grade(itemId, userId).map(GradingRepository.GradeRow::finalGrade);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Double> gradePercent(long itemId, long userId) {
        GradingRepository.ItemRow item = repository.item(itemId).orElse(null);
        if (item == null) {
            return Optional.empty();
        }
        Optional<Double> value = item.isTotal() ? Optional.ofNullable(totals(item.courseId(), userId).get(itemId))
                : grade(itemId, userId);
        return value.map(v -> (v - item.gradeMin()) * 100.0 / (item.gradeMax() - item.gradeMin()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isLocked(long itemId, long userId) {
        boolean itemLocked = repository.item(itemId).map(GradingRepository.ItemRow::locked).orElse(false);
        return itemLocked || repository.grade(itemId, userId).map(GradingRepository.GradeRow::locked).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Double> courseTotal(long courseId, long userId) {
        return repository.rootCategory(courseId).flatMap(root -> repository.totalItem(root.id()))
                .map(total -> totals(courseId, userId).get(total.id()));
    }

    /** Totals per total-item id for one user (computed, never stored). */
    @Transactional(readOnly = true)
    Map<Long, Double> totals(long courseId, long userId) {
        List<GradingRepository.CategoryRow> categories = repository.categories(courseId);
        List<GradingRepository.ItemRow> items = repository.items(courseId);
        Map<Long, Long> totalItemOf = new HashMap<>();
        items.stream().filter(GradingRepository.ItemRow::isTotal).forEach(i -> totalItemOf.put(i.categoryId(), i.id()));
        Map<Long, Double> grades = new HashMap<>();
        repository.grades(courseId, userId).forEach(g -> grades.put(g.itemId(), g.finalGrade()));
        return GradeCalculator.totals(
                categories.stream().filter(c -> totalItemOf.containsKey(c.id()))
                        .map(c -> new GradeCalculator.Category(c.id(), c.parentId(), c.aggregation(),
                                c.aggregateOnlyGraded(), c.dropLow(), c.keepHigh(), totalItemOf.get(c.id())))
                        .toList(),
                items.stream().map(i -> new GradeCalculator.Item(i.id(), i.categoryId(), i.isTotal(), i.gradeMin(),
                        i.gradeMax(), i.weight())).toList(),
                grades);
    }

    /** The course's top category and course total item, created on first use (Moodle creates them with the course). */
    GradingRepository.CategoryRow ensureRoot(long courseId) {
        return repository.rootCategory(courseId).orElseGet(() -> {
            long id = repository.insertCategory(courseId, null, "?", properties.defaultAggregation());
            repository.insertItem(courseId, id, null, "COURSE", null, 0, 100, null, 1.0);
            return repository.category(id).orElseThrow();
        });
    }

    GradingRepository.ItemRow requireItem(long itemId) {
        return repository.item(itemId).orElseThrow(() -> ApiException.notFound("UNKNOWN_GRADE_ITEM", "Unknown grade item " + itemId));
    }

    /** REQ-GRD-05: a grade is limited to the item's range (Moodle {@code grade_item::bounded_grade()}). */
    static Double bounded(GradingRepository.ItemRow item, Double grade) {
        if (grade == null) {
            return null;
        }
        return Math.max(item.gradeMin(), Math.min(item.gradeMax(), grade));
    }

    static void validateRange(double min, double max, Double pass) {
        if (max <= min) {
            throw ApiException.invalid("INVALID_GRADE_RANGE", "The maximum grade must be greater than the minimum grade.");
        }
        if (pass != null && (pass < min || pass > max)) {
            throw ApiException.invalid("INVALID_GRADE_PASS", "The grade to pass must be within the grade range.");
        }
    }

    static GradeItemInfo info(GradingRepository.ItemRow i) {
        return new GradeItemInfo(i.id(), i.courseId(), i.itemname(), i.source(), i.gradeMin(), i.gradeMax(), i.gradePass(),
                i.hidden(), i.locked());
    }
}
