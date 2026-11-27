package com.figlms.course.internal;

import com.figlms.course.api.CourseCatalog;
import com.figlms.course.api.CourseSummary;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
class DefaultCourseCatalog implements CourseCatalog {

    private final CourseRepository repository;

    DefaultCourseCatalog(CourseRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<CourseSummary> findByShortname(String shortname) {
        return shortname == null ? Optional.empty() : repository.courseByShortname(shortname);
    }

    @Override
    public Optional<CourseSummary> findById(long id) {
        return repository.course(id);
    }
}
