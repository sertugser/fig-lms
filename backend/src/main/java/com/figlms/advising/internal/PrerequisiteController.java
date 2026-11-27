package com.figlms.advising.internal;

import com.figlms.ApiException;
import com.figlms.advising.api.PrerequisiteCheck;
import com.figlms.advising.api.PrerequisiteService;
import com.figlms.advising.api.UnknownCourseException;
import com.figlms.course.api.AccessControl;
import com.figlms.course.api.CourseCatalog;
import com.figlms.identity.api.Capability;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/advising/prerequisite-check?student=<username>&course=<shortname>} (#6, used by golden master #13).
 *
 * <ul>
 *   <li>200 with a {@link PrerequisiteCheck} body: the decision, also when the student is not allowed.</li>
 *   <li>400 when a parameter is missing or blank, 404 when the student or course does not exist.</li>
 *   <li>401 without login; 403 when someone asks about another user without {@code report/completion:view} in the
 *       course (teachers of the course and the site administrator have it), as Moodle's
 *       {@code completion_can_view_data()}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/advising")
class PrerequisiteController {

    private final PrerequisiteService service;
    private final CourseCatalog courses;
    private final AccessControl access;

    PrerequisiteController(PrerequisiteService service, CourseCatalog courses, AccessControl access) {
        this.service = service;
        this.courses = courses;
        this.access = access;
    }

    @GetMapping("/prerequisite-check")
    PrerequisiteCheck check(@RequestParam String student, @RequestParam String course, Authentication caller) {
        if (student.isBlank() || course.isBlank()) {
            throw ApiException.invalid("INVALID_REQUEST", "student and course must not be blank");
        }
        if (!caller.getName().equals(student)) {
            long courseId = courses.findByShortname(course).orElseThrow(() -> new UnknownCourseException(course)).id();
            if (!access.has(caller.getName(), courseId, Capability.COMPLETION_VIEW_REPORT)) {
                throw ApiException.forbidden("FORBIDDEN", "A student may only check their own prerequisites");
            }
        }
        return service.check(student, course);
    }
}
