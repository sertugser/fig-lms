package com.figlms.course.internal;

import com.figlms.course.api.AccessControl;
import com.figlms.course.api.CourseSummary;
import com.figlms.identity.api.Role;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/course/...}: categories, courses and enrolment.
 *
 * <pre>
 * GET    /categories                          categories and courses the caller may see (REQ-CRS-10)
 * GET    /categories/{id}                     one of them; 404 if the caller may not see it
 * POST   /categories                          {name, idnumber, parentId, visible}            site admin
 * PUT    /categories/{id}/parent              {parentId}  move with subtree                  site admin
 * DELETE /categories/{id}?moveContentTo=id    delete, moving courses and subcategories       site admin
 * GET    /courses/defaults                    proposed start and end date for a new course
 * POST   /courses                             {fullname, shortname, idnumber, categoryId, startdate, enddate, visible}
 * GET    /courses/{shortname}                 summary, contacts, self enrolment open?
 * GET    /courses/{shortname}/access          200 if the caller may enter the course, else 403 NOT_ENROLLED
 * PUT    /courses/{shortname}/show-grades     {showGrades}
 * GET    /my-courses                          courses with an active enrolment
 * GET    /courses/{shortname}/participants
 * GET    /courses/{shortname}/assignable-roles
 * POST   /courses/{shortname}/enrolments      {username, role, timeStart, timeEnd}   manual enrolment
 * PUT    /courses/{shortname}/enrolments/{username}/suspended   {suspended}
 * POST   /courses/{shortname}/self-enrolment  {key}                                  enrol yourself
 * PUT    /courses/{shortname}/self-enrolment  {enabled, enrolmentKey, maxEnrolled, enrolStart, enrolEnd, enrolPeriodSeconds, allowNew}
 * </pre>
 */
@RestController
@RequestMapping("/api/course")
@EnableConfigurationProperties(CourseProperties.class)
class CourseController {

    record NewCategory(String name, String idnumber, Long parentId, Boolean visible) {
    }

    record ParentChange(Long parentId) {
    }

    record ShowGrades(boolean showGrades) {
    }

    record Suspension(boolean suspended) {
    }

    record SelfEnrolKey(String key) {
    }

    private final CategoryService categories;
    private final CourseService courses;
    private final EnrolmentService enrolments;
    private final AccessControl access;

    CourseController(CategoryService categories, CourseService courses, EnrolmentService enrolments,
            AccessControl access) {
        this.categories = categories;
        this.courses = courses;
        this.enrolments = enrolments;
        this.access = access;
    }

    @GetMapping("/categories")
    List<CategoryService.CategoryView> categories(Authentication caller) {
        return categories.view(caller.getName());
    }

    @GetMapping("/categories/{id}")
    CategoryService.CategoryView category(@PathVariable long id, Authentication caller) {
        return categories.viewOne(caller.getName(), id);
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    CourseRepository.CategoryRow createCategory(@RequestBody NewCategory body, Authentication caller) {
        return categories.create(caller.getName(), body.name(), body.idnumber(), body.parentId(), body.visible());
    }

    @PutMapping("/categories/{id}/parent")
    CourseRepository.CategoryRow moveCategory(@PathVariable long id, @RequestBody ParentChange body, Authentication caller) {
        return categories.move(caller.getName(), id, body.parentId());
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteCategory(@PathVariable long id, @RequestParam(required = false) Long moveContentTo, Authentication caller) {
        categories.delete(caller.getName(), id, moveContentTo);
    }

    @GetMapping("/courses/defaults")
    CourseService.CourseDefaults defaults() {
        return courses.defaults();
    }

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    CourseSummary createCourse(@RequestBody CourseService.NewCourse body, Authentication caller) {
        return courses.create(caller.getName(), body);
    }

    @GetMapping("/courses/{shortname}")
    CourseService.CourseDetails course(@PathVariable String shortname, Authentication caller) {
        return courses.details(caller.getName(), shortname);
    }

    @GetMapping("/courses/{shortname}/access")
    Map<String, Object> access(@PathVariable String shortname, Authentication caller) {
        CourseSummary course = courses.require(shortname);
        access.requireCourseAccess(caller.getName(), course.id());
        return Map.of("course", shortname, "access", true);
    }

    @PutMapping("/courses/{shortname}/show-grades")
    CourseSummary showGrades(@PathVariable String shortname, @RequestBody ShowGrades body, Authentication caller) {
        return courses.setShowGrades(caller.getName(), shortname, body.showGrades());
    }

    @GetMapping("/my-courses")
    List<CourseSummary> myCourses(Authentication caller) {
        return courses.myCourses(caller.getName());
    }

    @GetMapping("/courses/{shortname}/participants")
    List<EnrolmentService.Participant> participants(@PathVariable String shortname, Authentication caller) {
        return enrolments.participants(caller.getName(), shortname);
    }

    @GetMapping("/courses/{shortname}/assignable-roles")
    Set<Role> assignableRoles(@PathVariable String shortname, Authentication caller) {
        return enrolments.assignableRoles(caller.getName(), courses.require(shortname).id());
    }

    @PostMapping("/courses/{shortname}/enrolments")
    @ResponseStatus(HttpStatus.CREATED)
    EnrolmentService.EnrolmentView enrol(@PathVariable String shortname, @RequestBody EnrolmentService.ManualEnrolment body,
            Authentication caller) {
        return enrolments.enrolManually(caller.getName(), shortname, body);
    }

    @PutMapping("/courses/{shortname}/enrolments/{username}/suspended")
    EnrolmentService.EnrolmentView suspend(@PathVariable String shortname, @PathVariable String username,
            @RequestBody Suspension body, Authentication caller) {
        return enrolments.setStatus(caller.getName(), shortname, username, body.suspended());
    }

    @PostMapping("/courses/{shortname}/self-enrolment")
    @ResponseStatus(HttpStatus.CREATED)
    EnrolmentService.EnrolmentView enrolSelf(@PathVariable String shortname, @RequestBody(required = false) SelfEnrolKey body,
            Authentication caller) {
        return enrolments.enrolSelf(caller.getName(), shortname, body == null ? null : body.key());
    }

    @PutMapping("/courses/{shortname}/self-enrolment")
    CourseRepository.MethodRow configureSelf(@PathVariable String shortname, @RequestBody EnrolmentService.SelfSettings body,
            Authentication caller) {
        return enrolments.configureSelf(caller.getName(), shortname, body);
    }
}
