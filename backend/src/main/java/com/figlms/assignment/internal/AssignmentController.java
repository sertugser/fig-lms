package com.figlms.assignment.internal;

import java.time.Instant;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * {@code /api/assignment/...}.
 *
 * <pre>
 * GET  /courses/{shortname}/assignments                 assignments the caller can see
 * POST /courses/{shortname}/assignments                 create (AssignmentInput)                 editing teacher
 * GET  /assignments/{id}                                settings
 * PUT  /assignments/{id}                                change settings (AssignmentInput)        editing teacher
 * PUT  /assignments/{id}/overrides/{username}           {allowSubmissionsFrom, dueDate, cutoffDate}   editing teacher
 * DELETE /assignments/{id}/overrides/{username}
 * PUT  /assignments/{id}/extensions/{username}          {extensionDueDate}  (null removes)       grader
 * PUT  /assignments/{id}/locks/{username}               {locked}                                 grader
 *
 * GET  /assignments/{id}/submission                     own submission status
 * POST /assignments/{id}/submission                     multipart: onlineText, removeFileIds, files   save
 * POST /assignments/{id}/submission/submit              {acceptStatement}   submit a draft for grading
 *
 * GET  /assignments/{id}/submissions                    grading table                            grader
 * GET  /assignments/{id}/submissions/{username}         a student's status                       grader
 * PUT  /assignments/{id}/submissions/{username}/grade   {grade, attemptNumber, addAttempt}       grader
 * POST /assignments/{id}/submissions/{username}/attempts        allow another attempt            grader
 * POST /assignments/{id}/submissions/{username}/revert-to-draft                                  grader
 * </pre>
 *
 * Dates are ISO-8601 instants; {@code 1970-01-01T00:00:00Z} disables a date in the settings.
 */
@RestController
@RequestMapping("/api/assignment")
class AssignmentController {

    record Extension(Instant extensionDueDate) {
    }

    record Lock(boolean locked) {
    }

    record Submit(boolean acceptStatement) {
    }

    private final AssignmentService assignments;
    private final SubmissionService submissions;
    private final AssignmentGrading grading;

    AssignmentController(AssignmentService assignments, SubmissionService submissions, AssignmentGrading grading) {
        this.assignments = assignments;
        this.submissions = submissions;
        this.grading = grading;
    }

    @GetMapping("/courses/{shortname}/assignments")
    List<AssignmentService.AssignmentView> list(@PathVariable String shortname, Authentication caller) {
        return assignments.list(caller.getName(), shortname);
    }

    @PostMapping("/courses/{shortname}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    AssignmentService.AssignmentView create(@PathVariable String shortname,
            @RequestBody AssignmentService.AssignmentInput input, Authentication caller) {
        return assignments.create(caller.getName(), shortname, input);
    }

    @GetMapping("/assignments/{id}")
    AssignmentService.AssignmentView get(@PathVariable long id, Authentication caller) {
        return assignments.get(caller.getName(), id);
    }

    @PutMapping("/assignments/{id}")
    AssignmentService.AssignmentView update(@PathVariable long id, @RequestBody AssignmentService.AssignmentInput input,
            Authentication caller) {
        return assignments.update(caller.getName(), id, input);
    }

    @PutMapping("/assignments/{id}/overrides/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void setOverride(@PathVariable long id, @PathVariable String username,
            @RequestBody AssignmentService.DateOverride dates, Authentication caller) {
        assignments.setOverride(caller.getName(), id, username, dates);
    }

    @DeleteMapping("/assignments/{id}/overrides/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteOverride(@PathVariable long id, @PathVariable String username, Authentication caller) {
        assignments.deleteOverride(caller.getName(), id, username);
    }

    @PutMapping("/assignments/{id}/extensions/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void setExtension(@PathVariable long id, @PathVariable String username, @RequestBody Extension body,
            Authentication caller) {
        assignments.setExtension(caller.getName(), id, username, body.extensionDueDate());
    }

    @PutMapping("/assignments/{id}/locks/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void setLocked(@PathVariable long id, @PathVariable String username, @RequestBody Lock body, Authentication caller) {
        assignments.setLocked(caller.getName(), id, username, body.locked());
    }

    @GetMapping("/assignments/{id}/submission")
    SubmissionService.SubmissionView status(@PathVariable long id, Authentication caller) {
        return submissions.status(caller.getName(), id);
    }

    @PostMapping(path = "/assignments/{id}/submission", consumes = "multipart/form-data")
    SubmissionService.SubmissionView save(@PathVariable long id,
            @RequestParam(name = "onlineText", required = false) String onlineText,
            @RequestParam(name = "removeFileIds", required = false) List<Long> removeFileIds,
            @RequestPart(name = "files", required = false) List<MultipartFile> files, Authentication caller) {
        return submissions.save(caller.getName(), id, onlineText, removeFileIds, files);
    }

    @PostMapping("/assignments/{id}/submission/submit")
    SubmissionService.SubmissionView submit(@PathVariable long id, @RequestBody(required = false) Submit body,
            Authentication caller) {
        return submissions.submit(caller.getName(), id, body != null && body.acceptStatement());
    }

    @GetMapping("/assignments/{id}/submissions")
    List<AssignmentGrading.GradingRow> table(@PathVariable long id, Authentication caller) {
        return grading.table(caller.getName(), id);
    }

    @GetMapping("/assignments/{id}/submissions/{username}")
    SubmissionService.SubmissionView statusOf(@PathVariable long id, @PathVariable String username,
            Authentication caller) {
        return submissions.statusOf(caller.getName(), id, username);
    }

    @PutMapping("/assignments/{id}/submissions/{username}/grade")
    AssignmentGrading.GradeResult grade(@PathVariable long id, @PathVariable String username,
            @RequestBody AssignmentGrading.GradeInput input, Authentication caller) {
        return grading.grade(caller.getName(), id, username, input);
    }

    @PostMapping("/assignments/{id}/submissions/{username}/attempts")
    AssignmentGrading.GradeResult addAttempt(@PathVariable long id, @PathVariable String username,
            Authentication caller) {
        return grading.addAttempt(caller.getName(), id, username);
    }

    @PostMapping("/assignments/{id}/submissions/{username}/revert-to-draft")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revertToDraft(@PathVariable long id, @PathVariable String username, Authentication caller) {
        grading.revertToDraft(caller.getName(), id, username);
    }
}
