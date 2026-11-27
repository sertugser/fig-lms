package com.figlms.advising.internal;

import com.figlms.advising.api.PrerequisiteCheck;
import com.figlms.advising.api.PrerequisiteCheck.Reason;
import com.figlms.advising.api.PrerequisiteService;
import com.figlms.advising.api.UnknownCourseException;
import com.figlms.advising.api.UnknownStudentException;
import com.figlms.course.api.CourseCatalog;
import com.figlms.course.api.CourseSummary;
import com.figlms.identity.api.UserAccount;
import com.figlms.identity.api.UserDirectory;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class DefaultPrerequisiteService implements PrerequisiteService {

    private final AdvisingRepository repository;
    private final UserDirectory users;
    private final CourseCatalog courses;

    DefaultPrerequisiteService(AdvisingRepository repository, UserDirectory users, CourseCatalog courses) {
        this.repository = repository;
        this.users = users;
        this.courses = courses;
    }

    @Override
    public PrerequisiteCheck check(String student, String course) {
        UserAccount user = users.findByUsername(student).orElseThrow(() -> new UnknownStudentException(student));
        CourseSummary target = courses.findByShortname(course).orElseThrow(() -> new UnknownCourseException(course));
        List<String> prerequisites = repository.prerequisitesOf(target.id()).stream()
                .map(courses::findById).flatMap(Optional::stream).map(CourseSummary::shortname).sorted().toList();
        if (prerequisites.isEmpty()) {
            return new PrerequisiteCheck(student, course, true, Reason.NO_PREREQUISITES, List.of());
        }
        Set<Long> completed = repository.completedCourses(user.id());
        List<String> missing = prerequisites.stream()
                .filter(p -> courses.findByShortname(p).map(c -> !completed.contains(c.id())).orElse(true))
                .toList();
        return missing.isEmpty()
                ? new PrerequisiteCheck(student, course, true, Reason.PREREQUISITES_COMPLETED, List.of())
                : new PrerequisiteCheck(student, course, false, Reason.PREREQUISITES_MISSING, missing);
    }
}
