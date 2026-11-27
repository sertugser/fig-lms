package com.figlms.grading.internal;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/grading/...}
 *
 * <pre>
 * GET  /courses/{shortname}/users/{username}       user report (own grades, or anyone's for staff)
 * GET  /courses/{shortname}/report                 grader report, all students       moodle/grade:viewall
 * GET  /courses/{shortname}/categories             grade categories                  moodle/grade:viewall
 * POST /courses/{shortname}/categories             {fullname, parentId, aggregation} moodle/grade:manage
 * PUT  /courses/{shortname}/categories/{id}        {aggregation, aggregateOnlyGraded, dropLow, keepHigh}
 * POST /courses/{shortname}/items                  manual grade item                 moodle/grade:manage
 * PUT  /items/{id}                                 {name, gradeMax, gradePass, weight, hidden, locked}
 * PUT  /items/{id}/grades/{username}               {grade}  manual items only        moodle/grade:edit
 * PUT  /items/{id}/grades/{username}/flags         {hidden, locked}                  moodle/grade:edit
 * </pre>
 */
@RestController
@RequestMapping("/api/grading")
class GradingController {

    private final GradeReports reports;

    GradingController(GradeReports reports) {
        this.reports = reports;
    }

    @GetMapping("/courses/{shortname}/users/{username}")
    GradeReports.UserReport userReport(@PathVariable String shortname, @PathVariable String username, Authentication caller) {
        return reports.userReport(caller.getName(), shortname, username);
    }

    @GetMapping("/courses/{shortname}/report")
    GradeReports.GraderReport graderReport(@PathVariable String shortname, Authentication caller) {
        return reports.graderReport(caller.getName(), shortname);
    }

    @GetMapping("/courses/{shortname}/categories")
    List<GradingRepository.CategoryRow> categories(@PathVariable String shortname, Authentication caller) {
        return reports.categories(caller.getName(), shortname);
    }

    @PostMapping("/courses/{shortname}/categories")
    @ResponseStatus(HttpStatus.CREATED)
    GradingRepository.CategoryRow createCategory(@PathVariable String shortname, @RequestBody GradeReports.NewCategory body,
            Authentication caller) {
        return reports.createCategory(caller.getName(), shortname, body);
    }

    @PutMapping("/courses/{shortname}/categories/{id}")
    GradingRepository.CategoryRow updateCategory(@PathVariable String shortname, @PathVariable long id,
            @RequestBody GradeReports.CategorySettings body, Authentication caller) {
        return reports.updateCategory(caller.getName(), shortname, id, body);
    }

    @PostMapping("/courses/{shortname}/items")
    @ResponseStatus(HttpStatus.CREATED)
    GradeReports.GradeItemInfoView createItem(@PathVariable String shortname, @RequestBody GradeReports.NewItem body,
            Authentication caller) {
        return reports.createManualItem(caller.getName(), shortname, body);
    }

    @PutMapping("/items/{id}")
    GradeReports.GradeItemInfoView updateItem(@PathVariable long id, @RequestBody GradeReports.ItemChange body,
            Authentication caller) {
        return reports.updateItem(caller.getName(), id, body);
    }

    @PutMapping("/items/{id}/grades/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void setGrade(@PathVariable long id, @PathVariable String username, @RequestBody GradeReports.GradeChange body,
            Authentication caller) {
        reports.setGrade(caller.getName(), id, username, body);
    }

    @PutMapping("/items/{id}/grades/{username}/flags")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void setGradeFlags(@PathVariable long id, @PathVariable String username, @RequestBody GradeReports.GradeFlags body,
            Authentication caller) {
        reports.setGradeFlags(caller.getName(), id, username, body);
    }
}
