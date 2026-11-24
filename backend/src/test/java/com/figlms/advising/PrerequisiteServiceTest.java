package com.figlms.advising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.figlms.advising.api.PrerequisiteCheck;
import com.figlms.advising.api.PrerequisiteCheck.Reason;
import com.figlms.advising.api.PrerequisiteService;
import com.figlms.advising.api.UnknownCourseException;
import com.figlms.advising.api.UnknownStudentException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * REQ-ADV-09 on the sample data (same as Moodle's infra/sample-data): CS201 requires CS101, student1 and student2
 * completed CS101, student3 failed it, student4 and student5 have not completed it.
 */
@SpringBootTest(properties = "spring.profiles.active=test")
@Transactional
class PrerequisiteServiceTest {

    @Autowired
    PrerequisiteService service;

    @Autowired
    JdbcClient jdbc;

    @Test
    void normal_studentWhoCompletedThePrerequisiteIsAllowed() {
        assertThat(service.check("student1", "CS201"))
                .isEqualTo(new PrerequisiteCheck("student1", "CS201", true, Reason.PREREQUISITES_COMPLETED, List.of()));
    }

    @Test
    void normal_studentWhoFailedThePrerequisiteIsNotAllowed() {
        assertThat(service.check("student3", "CS201"))
                .isEqualTo(new PrerequisiteCheck("student3", "CS201", false, Reason.PREREQUISITES_MISSING,
                        List.of("CS101")));
    }

    @Test
    void boundary_courseWithoutPrerequisitesIsAllowedForEveryone() {
        assertThat(service.check("student5", "CS101"))
                .isEqualTo(new PrerequisiteCheck("student5", "CS101", true, Reason.NO_PREREQUISITES, List.of()));
    }

    @Test
    void boundary_completionRowWithoutTimeCompletedDoesNotCount() {
        // Moodle creates a course_completions row at enrolment; only timecompleted makes it complete.
        jdbc.sql("INSERT INTO adv_course_completion (username, course, time_completed) VALUES ('student4', 'CS101', NULL)")
                .update();
        assertThat(service.check("student4", "CS201").allowed()).isFalse();
    }

    @Test
    void boundary_allPrerequisitesMustBeCompleted() {
        jdbc.sql("INSERT INTO adv_prerequisite (course, prerequisite) VALUES ('CS201', 'MATH101')").update();

        PrerequisiteCheck check = service.check("student1", "CS201");

        assertThat(check.allowed()).isFalse();
        assertThat(check.missingPrerequisites()).containsExactly("MATH101");
    }

    @Test
    void boundary_missingPrerequisitesAreSorted() {
        jdbc.sql("INSERT INTO adv_prerequisite (course, prerequisite) VALUES ('CS201', 'MATH101')").update();

        assertThat(service.check("student3", "CS201").missingPrerequisites()).containsExactly("CS101", "MATH101");
    }

    @Test
    void invalid_unknownStudent() {
        assertThatThrownBy(() -> service.check("nobody", "CS201")).isInstanceOf(UnknownStudentException.class);
    }

    @Test
    void invalid_unknownCourse() {
        assertThatThrownBy(() -> service.check("student1", "CS999")).isInstanceOf(UnknownCourseException.class);
    }

    @Test
    void invalid_courseCannotBeItsOwnPrerequisite() {
        assertThatThrownBy(() -> jdbc.sql(
                "INSERT INTO adv_prerequisite (course, prerequisite) VALUES ('CS101', 'CS101')").update())
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
