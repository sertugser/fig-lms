-- Sample data, the same as infra/sample-data/load.php for Moodle, so golden master tests (#13) compare like with like.
-- CS201 requires CS101. student1 and student2 completed CS101; student3 failed it (grade 40, pass 50);
-- student4 was not graded; student5 did not submit.

INSERT INTO adv_course (shortname, fullname) VALUES
    ('CS101', 'Introduction to Programming'),
    ('CS201', 'Data Structures'),
    ('MATH101', 'Discrete Mathematics');

INSERT INTO adv_student (username) VALUES
    ('student1'), ('student2'), ('student3'), ('student4'), ('student5');

INSERT INTO adv_prerequisite (course, prerequisite) VALUES
    ('CS201', 'CS101');

INSERT INTO adv_course_completion (username, course, time_completed) VALUES
    ('student1', 'CS101', TIMESTAMP WITH TIME ZONE '2026-11-22 12:00:00+00'),
    ('student2', 'CS101', TIMESTAMP WITH TIME ZONE '2026-11-22 12:00:00+00');
