package com.figlms.advising.internal;

import com.figlms.ApiException;
import com.figlms.course.api.AccessControl;
import com.figlms.course.api.CourseCatalog;
import com.figlms.course.api.CourseSummary;
import com.figlms.identity.api.Capability;
import com.figlms.identity.api.Role;
import com.figlms.identity.api.UserAccount;
import com.figlms.identity.api.UserDirectory;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/advising/courses/{shortname}/...}: prerequisites and course completion.
 *
 * <pre>
 * GET  /prerequisites                       anyone who may see the course
 * PUT  /prerequisites   {prerequisites:[]}   moodle/course:update; no cycles
 * GET  /completion-criteria                 moodle/course:update
 * PUT  /completion-criteria {overall, methods, criteria:[{type, activityKey, otherCourse, role, gradePass, timeEnd}]}
 * GET  /completion/{username}               own, or report/completion:view
 * POST /completion/{username}/mark          moodle/course:markcomplete: ROLE criteria of the caller's roles
 * POST /completion/self                     SELF criterion
 * </pre>
 */
@RestController
@RequestMapping("/api/advising/courses/{shortname}")
@Transactional
class AdvisingController {

    record Prerequisites(List<String> prerequisites) {
    }

    record CriterionInput(CriterionType type, String activityKey, String otherCourse, Role role, Double gradePass,
            Instant timeEnd) {
    }

    record CriteriaInput(String overall, Map<CriterionType, String> methods, List<CriterionInput> criteria) {
    }

    private final AdvisingRepository repository;
    private final CompletionTracker tracker;
    private final CourseCatalog courses;
    private final AccessControl access;
    private final UserDirectory users;
    private final Clock clock;

    AdvisingController(AdvisingRepository repository, CompletionTracker tracker, CourseCatalog courses,
            AccessControl access, UserDirectory users, Clock clock) {
        this.repository = repository;
        this.tracker = tracker;
        this.courses = courses;
        this.access = access;
        this.users = users;
        this.clock = clock;
    }

    @GetMapping("/prerequisites")
    Prerequisites prerequisites(@PathVariable String shortname) {
        CourseSummary course = courses.require(shortname);
        return new Prerequisites(names(repository.prerequisitesOf(course.id())));
    }

    /** Replaces the prerequisites of the course. A course cannot require itself, directly or through others. */
    @PutMapping("/prerequisites")
    Prerequisites setPrerequisites(@PathVariable String shortname, @RequestBody Prerequisites body, Authentication caller) {
        CourseSummary course = courses.require(shortname);
        access.require(caller.getName(), course.id(), Capability.COURSE_UPDATE);
        List<Long> ids = new ArrayList<>();
        for (String name : body.prerequisites() == null ? List.<String>of() : body.prerequisites()) {
            CourseSummary p = courses.require(name);
            if (p.id() == course.id() || requires(p.id(), course.id(), new HashSet<>())) {
                throw ApiException.invalid("PREREQUISITE_CYCLE", "A course cannot require itself: " + name + " already requires "
                        + shortname + ".");
            }
            if (!ids.contains(p.id())) {
                ids.add(p.id());
            }
        }
        repository.replacePrerequisites(course.id(), ids);
        return prerequisites(shortname);
    }

    @GetMapping("/completion-criteria")
    List<AdvisingRepository.CriterionRow> criteria(@PathVariable String shortname, Authentication caller) {
        CourseSummary course = courses.require(shortname);
        access.require(caller.getName(), course.id(), Capability.COURSE_UPDATE);
        return repository.criteria(course.id());
    }

    @PutMapping("/completion-criteria")
    List<AdvisingRepository.CriterionRow> setCriteria(@PathVariable String shortname, @RequestBody CriteriaInput body,
            Authentication caller) {
        CourseSummary course = courses.require(shortname);
        access.require(caller.getName(), course.id(), Capability.COURSE_UPDATE);
        repository.deleteCriteria(course.id());
        for (CriterionInput c : body.criteria() == null ? List.<CriterionInput>of() : body.criteria()) {
            if (c.type() == null) {
                throw ApiException.invalid("INVALID_CRITERION", "Every criterion needs a type");
            }
            Long other = null;
            switch (c.type()) {
                case ACTIVITY -> repository.activity(c.activityKey()).filter(a -> a.courseId() == course.id())
                        .orElseThrow(() -> ApiException.invalid("INVALID_CRITERION", "Unknown activity in this course: "
                                + c.activityKey()));
                case COURSE -> {
                    other = courses.require(c.otherCourse()).id();
                    if (other == course.id()) {
                        throw ApiException.invalid("INVALID_CRITERION", "A course cannot depend on its own completion");
                    }
                }
                case ROLE -> {
                    if (c.role() == null || !c.role().isStaff()) {
                        throw ApiException.invalid("INVALID_CRITERION", "A role criterion needs a teacher or manager role");
                    }
                }
                case GRADE -> {
                    if (c.gradePass() == null) {
                        throw ApiException.invalid("INVALID_CRITERION", "A grade criterion needs gradePass");
                    }
                }
                case DATE -> {
                    if (c.timeEnd() == null) {
                        throw ApiException.invalid("INVALID_CRITERION", "A date criterion needs timeEnd");
                    }
                }
                case SELF -> {
                }
            }
            repository.insertCriterion(course.id(), c.type(), c.activityKey(), other,
                    c.role() == null ? null : c.role().name(), c.gradePass(), c.timeEnd());
        }
        repository.setAggregation(course.id(), "OVERALL", method(body.overall()));
        if (body.methods() != null) {
            body.methods().forEach((type, method) -> repository.setAggregation(course.id(), type.name(), method(method)));
        }
        return repository.criteria(course.id());
    }

    @GetMapping("/completion/{username}")
    CompletionTracker.CourseStatus completion(@PathVariable String shortname, @PathVariable String username,
            Authentication caller) {
        CourseSummary course = courses.require(shortname);
        UserAccount user = users.require(username);
        if (!caller.getName().equals(username)
                && !access.has(caller.getName(), course.id(), Capability.COMPLETION_VIEW_REPORT)) {
            throw ApiException.forbidden("CANNOT_VIEW_REPORT", "You cannot view this user's completion.");
        }
        return tracker.status(course.id(), user.id());
    }

    /** A teacher marks the student complete: every ROLE criterion of one of the caller's roles is met. */
    @PostMapping("/completion/{username}/mark")
    CompletionTracker.CourseStatus mark(@PathVariable String shortname, @PathVariable String username,
            Authentication caller) {
        CourseSummary course = courses.require(shortname);
        access.require(caller.getName(), course.id(), Capability.MARK_COMPLETE);
        UserAccount marker = users.require(caller.getName());
        UserAccount student = users.require(username);
        Set<Role> roles = marker.siteAdmin() ? Set.of(Role.values()) : access.rolesIn(marker.id(), course.id());
        List<AdvisingRepository.CriterionRow> markable = repository.criteria(course.id()).stream()
                .filter(c -> c.type() == CriterionType.ROLE && roles.contains(Role.valueOf(c.role()))).toList();
        if (markable.isEmpty()) {
            throw ApiException.invalid("NOTHING_TO_MARK", "This course has no completion criterion for your role.");
        }
        markable.forEach(c -> repository.mark(c.id(), student.id(), marker.id(), clock.instant()));
        tracker.reaggregate(course.id(), student.id());
        return tracker.status(course.id(), student.id());
    }

    @PostMapping("/completion/self")
    CompletionTracker.CourseStatus markSelf(@PathVariable String shortname, Authentication caller) {
        CourseSummary course = courses.require(shortname);
        access.requireCourseAccess(caller.getName(), course.id());
        UserAccount student = users.require(caller.getName());
        Optional<AdvisingRepository.CriterionRow> self = repository.criteria(course.id()).stream()
                .filter(c -> c.type() == CriterionType.SELF).findFirst();
        AdvisingRepository.CriterionRow criterion = self.orElseThrow(
                () -> ApiException.invalid("NOTHING_TO_MARK", "This course has no self completion."));
        repository.mark(criterion.id(), student.id(), student.id(), clock.instant());
        tracker.reaggregate(course.id(), student.id());
        return tracker.status(course.id(), student.id());
    }

    private boolean requires(long courseId, long target, Set<Long> seen) {
        if (!seen.add(courseId)) {
            return false;
        }
        for (Long p : repository.prerequisitesOf(courseId)) {
            if (p == target || requires(p, target, seen)) {
                return true;
            }
        }
        return false;
    }

    private List<String> names(List<Long> ids) {
        return ids.stream().map(courses::findById).flatMap(Optional::stream).map(CourseSummary::shortname).sorted().toList();
    }

    private static String method(String m) {
        if (m == null) {
            return "ALL";
        }
        if (!m.equals("ALL") && !m.equals("ANY")) {
            throw ApiException.invalid("INVALID_AGGREGATION", "Aggregation must be ALL or ANY");
        }
        return m;
    }
}
