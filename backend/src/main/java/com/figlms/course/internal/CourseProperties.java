package com.figlms.course.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Site settings of the course module ({@code fig.course.*}); defaults are Moodle 5.3.1's.
 *
 * @param enrolAdminInNewCourse Moodle {@code enroladminnewcourse} (default on): the site administrator who creates a
 *                              course is enrolled in it as editing teacher ({@code creatornewroleid})
 * @param browseAllCourses      Moodle {@code moodle/category:viewcourselist} for logged-in users (default allowed).
 *                              {@code false} is the setup of MDL-85503: users then see only the categories and courses
 *                              they are enrolled in, instead of nothing (REQ-CRS-10)
 * @param courseDuration        Moodle {@code moodlecourse/courseduration} (default one year), used for the proposed
 *                              end date of a new course
 */
@ConfigurationProperties("fig.course")
record CourseProperties(@DefaultValue("true") boolean enrolAdminInNewCourse,
        @DefaultValue("true") boolean browseAllCourses, @DefaultValue("365d") Duration courseDuration) {
}
