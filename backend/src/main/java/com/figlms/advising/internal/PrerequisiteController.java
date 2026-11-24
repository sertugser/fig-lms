package com.figlms.advising.internal;

import com.figlms.advising.api.PrerequisiteCheck;
import com.figlms.advising.api.PrerequisiteService;
import com.figlms.advising.api.UnknownCourseException;
import com.figlms.advising.api.UnknownStudentException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/advising/prerequisite-check?student=<username>&course=<shortname>} (#6).
 *
 * <ul>
 *   <li>200 with a {@link PrerequisiteCheck} body: the decision, also when the student is not allowed.</li>
 *   <li>400 when a parameter is missing or blank, 404 when the student or course does not exist.</li>
 *   <li>401 without login; 403 when a student asks about another student. Teachers and admins may ask about anyone.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/advising")
class PrerequisiteController {

    private final PrerequisiteService service;

    PrerequisiteController(PrerequisiteService service) {
        this.service = service;
    }

    @GetMapping("/prerequisite-check")
    PrerequisiteCheck check(@RequestParam String student, @RequestParam String course, Authentication caller) {
        if (student.isBlank() || course.isBlank()) {
            throw new InvalidRequestException("student and course must not be blank");
        }
        boolean staff = caller.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_TEACHER") || a.getAuthority().equals("ROLE_ADMIN"));
        if (!staff && !caller.getName().equals(student)) {
            throw new AccessDeniedException("A student may only check their own prerequisites");
        }
        return service.check(student, course);
    }

    @ExceptionHandler({UnknownStudentException.class, UnknownCourseException.class})
    ResponseEntity<Map<String, String>> notFound(RuntimeException e) {
        String code = e instanceof UnknownStudentException ? "UNKNOWN_STUDENT" : "UNKNOWN_COURSE";
        return error(HttpStatus.NOT_FOUND, code, e.getMessage());
    }

    @ExceptionHandler({InvalidRequestException.class, MissingServletRequestParameterException.class})
    ResponseEntity<Map<String, String>> badRequest(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e.getMessage());
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("error", code, "message", message));
    }

    static class InvalidRequestException extends RuntimeException {
        InvalidRequestException(String message) {
            super(message);
        }
    }
}
