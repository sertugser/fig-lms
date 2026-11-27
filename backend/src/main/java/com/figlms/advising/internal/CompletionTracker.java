package com.figlms.advising.internal;

import com.figlms.ApiException;
import com.figlms.advising.api.CompletionService;
import com.figlms.advising.api.CompletionState;
import com.figlms.grading.api.GradebookService;
import com.figlms.identity.api.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Activity and course completion (REQ-ADV-02, REQ-ADV-05), Moodle's {@code completion_info} without the cron delay:
 * every change re-aggregates the affected courses for that user at once, including courses that depend on them
 * through "completion of other courses". As in Moodle, a completed course is never marked incomplete again.
 */
@Service
@Transactional
class CompletionTracker implements CompletionService {

    /** One criterion and whether the user meets it. */
    record CriterionStatus(long id, CriterionType type, String description, boolean met) {
    }

    record CourseStatus(boolean tracked, boolean complete, Instant timeCompleted, String overall,
            List<CriterionStatus> criteria) {
    }

    private final AdvisingRepository repository;
    private final GradebookService gradebook;
    private final Clock clock;

    CompletionTracker(AdvisingRepository repository, GradebookService gradebook, Clock clock) {
        this.repository = repository;
        this.gradebook = gradebook;
        this.clock = clock;
    }

    @Override
    public void registerActivity(long courseId, String activityKey, String name) {
        repository.upsertActivity(activityKey, courseId, name);
    }

    @Override
    public void removeActivity(String activityKey) {
        repository.markActivityDeleted(activityKey);
    }

    @Override
    public void recordActivityCompletion(String activityKey, long userId, CompletionState state) {
        AdvisingRepository.ActivityRow activity = repository.activity(activityKey).orElseThrow(
                () -> ApiException.notFound("UNKNOWN_ACTIVITY", "Unknown activity " + activityKey));
        repository.upsertActivityState(activityKey, userId, state, clock.instant());
        Set<Long> courses = new HashSet<>(repository.coursesWithActivityCriterion(activityKey));
        courses.add(activity.courseId());
        courses.forEach(c -> reaggregate(c, userId));
    }

    @Override
    @Transactional(readOnly = true)
    public CompletionState activityState(String activityKey, long userId) {
        return repository.activityState(activityKey, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCourseComplete(long courseId, long userId) {
        return repository.courseCompletedAt(courseId, userId).isPresent();
    }

    /**
     * Updates the user's completion of {@code courseId}; when it becomes complete, the courses that require it
     * ("completion of other courses") are updated too.
     */
    void reaggregate(long courseId, long userId) {
        Deque<Long> queue = new ArrayDeque<>(List.of(courseId));
        Set<Long> seen = new HashSet<>();
        while (!queue.isEmpty()) {
            long course = queue.pop();
            if (!seen.add(course) || isCourseComplete(course, userId)) {
                continue;
            }
            if (status(course, userId).complete()) {
                repository.markCourseComplete(course, userId, clock.instant());
                queue.addAll(repository.coursesDependingOn(course));
            }
        }
    }

    /** Evaluates every criterion of the course for the user and aggregates them (REQ-ADV-05). */
    @Transactional(readOnly = true)
    CourseStatus status(long courseId, long userId) {
        List<AdvisingRepository.CriterionRow> criteria = repository.criteria(courseId);
        Instant done = repository.courseCompletedAt(courseId, userId).orElse(null);
        String overall = repository.aggregation(courseId, "OVERALL");
        if (criteria.isEmpty()) {
            return new CourseStatus(false, done != null, done, overall, List.of());
        }
        List<CriterionStatus> statuses = new ArrayList<>();
        Map<CriterionType, List<Boolean>> byType = new LinkedHashMap<>();
        List<Boolean> parts = new ArrayList<>();
        for (AdvisingRepository.CriterionRow c : criteria) {
            boolean met = isMet(c, userId);
            statuses.add(new CriterionStatus(c.id(), c.type(), describe(c), met));
            if (c.type().aggregatedWithinType()) {
                byType.computeIfAbsent(c.type(), t -> new ArrayList<>()).add(met);
            } else {
                parts.add(met);
            }
        }
        byType.forEach((type, results) -> parts.add(combine(repository.aggregation(courseId, type.name()), results)));
        boolean complete = done != null || combine(overall, parts);
        return new CourseStatus(true, complete, done, overall, statuses);
    }

    private boolean isMet(AdvisingRepository.CriterionRow c, long userId) {
        return switch (c.type()) {
            case ACTIVITY -> {
                CompletionState state = repository.activityState(c.activityKey(), userId);
                yield state == CompletionState.COMPLETE || state == CompletionState.COMPLETE_PASS;
            }
            case COURSE -> c.otherCourseId() != null && isCourseComplete(c.otherCourseId(), userId);
            case ROLE, SELF -> repository.isMarked(c.id(), userId);
            case DATE -> c.timeEnd() != null && !clock.instant().isBefore(c.timeEnd());
            case GRADE -> c.gradePass() != null
                    && gradebook.courseTotal(c.courseId(), userId).map(total -> total >= c.gradePass()).orElse(false);
        };
    }

    private String describe(AdvisingRepository.CriterionRow c) {
        return switch (c.type()) {
            case ACTIVITY -> "Activity completion: " + repository.activity(c.activityKey())
                    .map(AdvisingRepository.ActivityRow::name).orElse(c.activityKey());
            case COURSE -> "Completion of other course (id " + c.otherCourseId() + ")";
            case ROLE -> "Marked complete by " + Role.valueOf(c.role()).moodleShortname();
            case SELF -> "Self completion";
            case DATE -> "Date: " + c.timeEnd();
            case GRADE -> "Course grade at least " + c.gradePass();
        };
    }

    private static boolean combine(String method, List<Boolean> results) {
        return "ANY".equals(method) ? results.stream().anyMatch(b -> b) : results.stream().allMatch(b -> b);
    }
}
