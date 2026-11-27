package com.figlms.grading.internal;

import com.figlms.ApiException;
import com.figlms.course.api.AccessControl;
import com.figlms.course.api.CourseCatalog;
import com.figlms.course.api.CourseSummary;
import com.figlms.identity.api.Capability;
import com.figlms.identity.api.Role;
import com.figlms.identity.api.UserAccount;
import com.figlms.identity.api.UserDirectory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reports and gradebook setup for people (REQ-GRD-01..08), with the access rules of Moodle's grade reports. */
@Service
@Transactional
class GradeReports {

    record ItemView(long id, String name, String type, Double grade, double gradeMin, double gradeMax, Double gradePass,
            double weight, boolean hidden, boolean locked) {
    }

    /**
     * @param total       course total, {@code null} if empty or hidden from the caller
     * @param totalHidden {@code true} when the total is withheld because it contains hidden grades (REQ-GRD-07,
     *                    Moodle user report default "hide totals if they contain hidden items")
     */
    record UserReport(String course, String username, String fullName, List<ItemView> items, Double total,
            boolean totalHidden) {
    }

    record GraderReport(String course, List<ItemView> items, List<UserReport> students) {
    }

    record NewItem(String name, Double gradeMin, Double gradeMax, Double gradePass, Double weight, Long categoryId) {
    }

    record ItemChange(String name, Double gradeMax, Double gradePass, Double weight, Boolean hidden, Boolean locked) {
    }

    record NewCategory(String fullname, Long parentId, Aggregation aggregation) {
    }

    record CategorySettings(Aggregation aggregation, Boolean aggregateOnlyGraded, Integer dropLow, Integer keepHigh) {
    }

    record GradeChange(Double grade) {
    }

    record GradeFlags(boolean hidden, boolean locked) {
    }

    private final GradingRepository repository;
    private final Gradebook gradebook;
    private final CourseCatalog courses;
    private final AccessControl access;
    private final UserDirectory users;
    private final java.time.Clock clock;

    GradeReports(GradingRepository repository, Gradebook gradebook, CourseCatalog courses, AccessControl access,
            UserDirectory users, java.time.Clock clock) {
        this.clock = clock;
        this.repository = repository;
        this.gradebook = gradebook;
        this.courses = courses;
        this.access = access;
        this.users = users;
    }

    /**
     * REQ-GRD-06: a student reads only their own grades, and only with {@code moodle/grade:view} and "Show gradebook
     * to students" on. Staff with {@code moodle/grade:viewall} read anyone's.
     */
    @Transactional(readOnly = true)
    UserReport userReport(String caller, String shortname, String username) {
        CourseSummary course = courses.require(shortname);
        UserAccount target = users.require(username);
        boolean viewAll = access.has(caller, course.id(), Capability.GRADE_VIEW_ALL);
        if (!viewAll) {
            boolean own = caller.equals(username) && access.has(caller, course.id(), Capability.GRADE_VIEW_OWN)
                    && course.showGrades();
            if (!own) {
                throw noPermission();
            }
        }
        if (!access.isEnrolled(target.id(), course.id(), false)) {
            throw ApiException.notFound("USER_NOT_ENROLLED", username + " is not enrolled in " + shortname);
        }
        gradebook.ensureRoot(course.id());
        boolean viewHidden = access.has(caller, course.id(), Capability.GRADE_VIEW_HIDDEN);
        return report(course, target, viewHidden);
    }

    /** All students' grades: {@code moodle/grade:viewall} only (REQ-GRD-08). */
    @Transactional(readOnly = true)
    GraderReport graderReport(String caller, String shortname) {
        CourseSummary course = courses.require(shortname);
        if (!access.has(caller, course.id(), Capability.GRADE_VIEW_ALL)) {
            throw ApiException.forbidden("NO_PERMISSIONS", "Sorry, but you do not currently have permissions to do that ("
                    + Capability.GRADE_VIEW_ALL.moodleName() + ").");
        }
        gradebook.ensureRoot(course.id());
        List<UserReport> students = new ArrayList<>();
        for (Long userId : access.usersWithRole(course.id(), Role.STUDENT, true)) {
            users.findById(userId).ifPresent(u -> students.add(report(course, u, true)));
        }
        List<ItemView> items = repository.items(course.id()).stream().filter(i -> !i.isTotal())
                .map(i -> view(i, null)).toList();
        return new GraderReport(shortname, items, students);
    }

    GradeItemInfoView createManualItem(String caller, String shortname, NewItem body) {
        CourseSummary course = courses.require(shortname);
        access.require(caller, course.id(), Capability.GRADE_MANAGE);
        GradingRepository.CategoryRow root = gradebook.ensureRoot(course.id());
        long categoryId = body.categoryId() == null ? root.id() : requireCategory(course.id(), body.categoryId()).id();
        double min = body.gradeMin() == null ? 0 : body.gradeMin();
        double max = body.gradeMax() == null ? 100 : body.gradeMax();
        Gradebook.validateRange(min, max, body.gradePass());
        if (body.name() == null || body.name().isBlank()) {
            throw ApiException.invalid("ITEM_NAME_REQUIRED", "The grade item needs a name");
        }
        long id = repository.insertItem(course.id(), categoryId, body.name().strip(), "MANUAL", null, min, max,
                body.gradePass(), body.weight() == null ? 1 : body.weight());
        return GradeItemInfoView.of(gradebook.requireItem(id));
    }

    GradeItemInfoView updateItem(String caller, long itemId, ItemChange body) {
        GradingRepository.ItemRow item = gradebook.requireItem(itemId);
        access.require(caller, item.courseId(), Capability.GRADE_MANAGE);
        double max = body.gradeMax() == null ? item.gradeMax() : body.gradeMax();
        Double pass = body.gradePass() == null ? item.gradePass() : body.gradePass();
        Gradebook.validateRange(item.gradeMin(), max, pass);
        repository.updateItem(itemId, body.name() == null ? item.itemname() : body.name(), max, pass,
                body.weight() == null ? item.weight() : body.weight(),
                body.hidden() == null ? item.hidden() : body.hidden(), body.locked() == null ? item.locked() : body.locked());
        return GradeItemInfoView.of(gradebook.requireItem(itemId));
    }

    /** Grades of manual items. Activity items are graded in their activity, which pushes the grade here. */
    void setGrade(String caller, long itemId, String username, GradeChange body) {
        GradingRepository.ItemRow item = gradebook.requireItem(itemId);
        access.require(caller, item.courseId(), Capability.GRADE_EDIT);
        if (!"MANUAL".equals(item.itemtype())) {
            throw ApiException.conflict("GRADE_IN_ACTIVITY", "This grade belongs to an activity; grade it there.");
        }
        UserAccount target = requireEnrolled(item.courseId(), username);
        gradebook.pushGrade(itemId, target.id(), body.grade(), users.require(caller).id());
    }

    void setGradeFlags(String caller, long itemId, String username, GradeFlags body) {
        GradingRepository.ItemRow item = gradebook.requireItem(itemId);
        access.require(caller, item.courseId(), Capability.GRADE_EDIT);
        UserAccount target = requireEnrolled(item.courseId(), username);
        repository.setGradeFlags(itemId, target.id(), body.hidden(), body.locked(), clock.instant());
    }

    GradingRepository.CategoryRow createCategory(String caller, String shortname, NewCategory body) {
        CourseSummary course = courses.require(shortname);
        access.require(caller, course.id(), Capability.GRADE_MANAGE);
        GradingRepository.CategoryRow root = gradebook.ensureRoot(course.id());
        GradingRepository.CategoryRow parent = body.parentId() == null ? root : requireCategory(course.id(), body.parentId());
        if (body.fullname() == null || body.fullname().isBlank()) {
            throw ApiException.invalid("CATEGORY_NAME_REQUIRED", "The grade category needs a name");
        }
        long id = repository.insertCategory(course.id(), parent.id(), body.fullname().strip(),
                body.aggregation() == null ? root.aggregation() : body.aggregation());
        repository.insertItem(course.id(), id, null, "CATEGORY", null, 0, 100, null, 1.0);
        return repository.category(id).orElseThrow();
    }

    /** REQ-GRD-01, 03, 04. When both limit rules are set, drop lowest wins and keep highest is reset to 0. */
    GradingRepository.CategoryRow updateCategory(String caller, String shortname, long categoryId, CategorySettings body) {
        CourseSummary course = courses.require(shortname);
        access.require(caller, course.id(), Capability.GRADE_MANAGE);
        GradingRepository.CategoryRow category = requireCategory(course.id(), categoryId);
        int dropLow = body.dropLow() == null ? category.dropLow() : body.dropLow();
        int keepHigh = body.keepHigh() == null ? category.keepHigh() : body.keepHigh();
        if (dropLow < 0 || keepHigh < 0) {
            throw ApiException.invalid("INVALID_LIMIT_RULE", "Drop lowest and keep highest cannot be negative.");
        }
        if (dropLow > 0) {
            keepHigh = 0;
        }
        repository.updateCategory(categoryId, body.aggregation() == null ? category.aggregation() : body.aggregation(),
                body.aggregateOnlyGraded() == null ? category.aggregateOnlyGraded() : body.aggregateOnlyGraded(),
                dropLow, keepHigh);
        return repository.category(categoryId).orElseThrow();
    }

    @Transactional(readOnly = true)
    List<GradingRepository.CategoryRow> categories(String caller, String shortname) {
        CourseSummary course = courses.require(shortname);
        access.require(caller, course.id(), Capability.GRADE_VIEW_ALL);
        gradebook.ensureRoot(course.id());
        return repository.categories(course.id());
    }

    private UserReport report(CourseSummary course, UserAccount user, boolean viewHidden) {
        List<GradingRepository.ItemRow> items = repository.items(course.id());
        Map<Long, GradingRepository.GradeRow> grades = new HashMap<>();
        repository.grades(course.id(), user.id()).forEach(g -> grades.put(g.itemId(), g));
        Map<Long, Double> totals = gradebook.totals(course.id(), user.id());
        boolean containsHidden = false;
        List<ItemView> views = new ArrayList<>();
        GradingRepository.ItemRow courseTotal = null;
        for (GradingRepository.ItemRow item : items) {
            if ("COURSE".equals(item.itemtype())) {
                courseTotal = item;
                continue;
            }
            GradingRepository.GradeRow grade = grades.get(item.id());
            boolean hidden = item.hidden() || (grade != null && grade.hidden());
            containsHidden |= hidden && !item.isTotal();
            if (hidden && !viewHidden) {
                continue;
            }
            Double value = item.isTotal() ? totals.get(item.id()) : grade == null ? null : grade.finalGrade();
            views.add(view(item, value));
        }
        Double total = courseTotal == null ? null : totals.get(courseTotal.id());
        boolean withhold = containsHidden && !viewHidden;
        return new UserReport(course.shortname(), user.username(), user.fullName(), views,
                withhold ? null : round(total), withhold);
    }

    private UserAccount requireEnrolled(long courseId, String username) {
        UserAccount target = users.require(username);
        if (!access.isEnrolled(target.id(), courseId, false)) {
            throw ApiException.notFound("USER_NOT_ENROLLED", username + " is not enrolled in this course");
        }
        return target;
    }

    private GradingRepository.CategoryRow requireCategory(long courseId, long categoryId) {
        return repository.category(categoryId).filter(c -> c.courseId() == courseId)
                .orElseThrow(() -> ApiException.notFound("UNKNOWN_GRADE_CATEGORY", "Unknown grade category " + categoryId));
    }

    private static ItemView view(GradingRepository.ItemRow item, Double grade) {
        return new ItemView(item.id(), item.itemname(), item.itemtype(), round(grade), item.gradeMin(), item.gradeMax(),
                item.gradePass(), item.weight(), item.hidden(), item.locked());
    }

    /** Two decimals, as Moodle shows grades by default. */
    static Double round(Double value) {
        return value == null ? null : Math.round(value * 100.0) / 100.0;
    }

    private static ApiException noPermission() {
        return ApiException.forbidden("NO_PERMISSION_TO_VIEW_GRADES", "You do not have permission to view these grades");
    }

    record GradeItemInfoView(long id, String name, String type, double gradeMin, double gradeMax, Double gradePass,
            double weight, boolean hidden, boolean locked, long categoryId) {

        static GradeItemInfoView of(GradingRepository.ItemRow i) {
            return new GradeItemInfoView(i.id(), i.itemname(), i.itemtype(), i.gradeMin(), i.gradeMax(), i.gradePass(),
                    i.weight(), i.hidden(), i.locked(), i.categoryId());
        }
    }
}
