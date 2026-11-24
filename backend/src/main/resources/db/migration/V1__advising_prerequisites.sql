-- Advising module (#6): course prerequisites and course completions.
-- Courses are identified by their short name and students by their username, as in the golden master snapshots.
-- These simple tables move behind the course and grading module APIs once those modules store data.

CREATE TABLE adv_course (
    shortname VARCHAR(255) PRIMARY KEY,
    fullname  VARCHAR(1333) NOT NULL
);

CREATE TABLE adv_student (
    username VARCHAR(100) PRIMARY KEY
);

-- "course" requires "prerequisite" to be completed before enrolment (REQ-ADV-09). All rows of a course are combined with AND.
CREATE TABLE adv_prerequisite (
    course       VARCHAR(255) NOT NULL REFERENCES adv_course (shortname),
    prerequisite VARCHAR(255) NOT NULL REFERENCES adv_course (shortname),
    PRIMARY KEY (course, prerequisite),
    CONSTRAINT adv_prerequisite_not_self CHECK (course <> prerequisite)
);

-- Same meaning as Moodle's course_completions: a course is completed when time_completed is set.
CREATE TABLE adv_course_completion (
    username       VARCHAR(100) NOT NULL REFERENCES adv_student (username),
    course         VARCHAR(255) NOT NULL REFERENCES adv_course (shortname),
    time_completed TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (username, course)
);
