package com.figlms.advising.internal;

import com.figlms.advising.api.PrerequisiteCheck;
import com.figlms.advising.api.PrerequisiteCheck.Reason;
import com.figlms.advising.api.PrerequisiteService;
import com.figlms.advising.api.UnknownCourseException;
import com.figlms.advising.api.UnknownStudentException;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class DefaultPrerequisiteService implements PrerequisiteService {

    private final AdvisingRepository repository;

    DefaultPrerequisiteService(AdvisingRepository repository) {
        this.repository = repository;
    }

    @Override
    public PrerequisiteCheck check(String student, String course) {
        if (!repository.studentExists(student)) {
            throw new UnknownStudentException(student);
        }
        if (!repository.courseExists(course)) {
            throw new UnknownCourseException(course);
        }
        List<String> prerequisites = repository.prerequisitesOf(course);
        if (prerequisites.isEmpty()) {
            return new PrerequisiteCheck(student, course, true, Reason.NO_PREREQUISITES, List.of());
        }
        Set<String> completed = repository.completedCourses(student);
        List<String> missing = prerequisites.stream().filter(p -> !completed.contains(p)).sorted().toList();
        return missing.isEmpty()
                ? new PrerequisiteCheck(student, course, true, Reason.PREREQUISITES_COMPLETED, List.of())
                : new PrerequisiteCheck(student, course, false, Reason.PREREQUISITES_MISSING, missing);
    }
}
