package com.figlms.advising.api;

/**
 * Course prerequisite check (REQ-ADV-09, MDL-22648): may a student enrol in a course?
 *
 * <p>Moodle has no such check: a course can only use "Completion of other courses" as a completion criterion,
 * and enrolment ignores it (REQ-ADV-08). Other modules, such as course enrolment and the study planner, call this
 * service before they let a student into a course.
 */
public interface PrerequisiteService {

    /**
     * Checks the prerequisites of {@code course} for {@code student}. All prerequisites must be completed (AND),
     * the same aggregation Moodle uses by default for "Completion of other courses".
     *
     * @param student username of the student, for example {@code student3}
     * @param course  short name of the course, for example {@code CS201}
     * @return the decision and the reason for it
     * @throws UnknownStudentException if there is no student with this username
     * @throws UnknownCourseException  if there is no course with this short name
     */
    PrerequisiteCheck check(String student, String course);
}
