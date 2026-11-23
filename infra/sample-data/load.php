<?php
// Fig LMS sample data for Moodle 5.3.1.
// Run with infra/sample-data/load.sh (it calls this script inside the moodle container).
//
// Creates:
//   - 1 category  : Engineering (idnumber FIG-ENG)
//   - 3 courses   : CS101 Introduction to Programming
//                   CS201 Data Structures   -> prerequisite: completion of CS101 (course completion criterion)
//                   MATH101 Discrete Mathematics
//   - 2 teachers  : teacher1 (CS101, CS201), teacher2 (MATH101)          role: editingteacher
//   - 5 students  : student1..student5                                    role: student
//   - 1 assignment: "Assignment 1" in CS101 (max 100, pass 50, online text + file submission)
//   - grades      : student1 = 85, student2 = 72, student3 = 40 (student4, student5 not graded)
//   - completion  : student1 and student2 completed CS101; student3 failed, student4/5 not graded
//
// Idempotent: if category FIG-ENG already exists, nothing is created. To start again from scratch:
//   docker compose -f infra/docker-compose.yml down -v && docker compose -f infra/docker-compose.yml up -d

define('CLI_SCRIPT', true);
require('/var/www/moodle/config.php');
require_once($CFG->libdir . '/clilib.php');
require_once($CFG->libdir . '/completionlib.php');
require_once($CFG->dirroot . '/completion/criteria/completion_criteria_activity.php');
require_once($CFG->dirroot . '/completion/criteria/completion_criteria_course.php');
require_once($CFG->dirroot . '/completion/criteria/completion_criteria_role.php');
require_once($CFG->dirroot . '/completion/completion_completion.php');
require_once($CFG->dirroot . '/mod/assign/locallib.php');

if ($DB->record_exists('course_categories', ['idnumber' => 'FIG-ENG'])) {
    echo "Sample data already loaded (category FIG-ENG exists). Nothing to do.\n";
    exit(0);
}

$password = getenv('SAMPLE_USER_PASSWORD') ?: '';
if ($password === '') {
    cli_error('SAMPLE_USER_PASSWORD is empty. Set it in infra/.env.');
}

\core\session\manager::set_user(get_admin());
set_config('enablecompletion', 1);

$gen = \core\test\phpunit\phpunit_util::get_data_generator();
$transaction = $DB->start_delegated_transaction();

// Category and courses. Dates: autumn term 2026.
$start = make_timestamp(2026, 9, 28);
$end = make_timestamp(2027, 1, 22);

$category = $gen->create_category([
    'name' => 'Engineering',
    'idnumber' => 'FIG-ENG',
    'description' => 'Fig LMS sample data',
]);

$coursedefaults = [
    'category' => $category->id,
    'startdate' => $start,
    'enddate' => $end,
    'enablecompletion' => 1,
    'format' => 'topics',
    'numsections' => 4,
];
$cs101 = $gen->create_course($coursedefaults + [
    'fullname' => 'Introduction to Programming', 'shortname' => 'CS101', 'idnumber' => 'CS101',
]);
$cs201 = $gen->create_course($coursedefaults + [
    'fullname' => 'Data Structures', 'shortname' => 'CS201', 'idnumber' => 'CS201',
]);
$math101 = $gen->create_course($coursedefaults + [
    'fullname' => 'Discrete Mathematics', 'shortname' => 'MATH101', 'idnumber' => 'MATH101',
]);

// Users.
$mkuser = function (string $username, string $first, string $last) use ($gen, $password) {
    return $gen->create_user([
        'username' => $username,
        'password' => $password,
        'firstname' => $first,
        'lastname' => $last,
        'email' => $username . '@example.com',
        'idnumber' => strtoupper($username),
    ]);
};
$teacher1 = $mkuser('teacher1', 'Ayse', 'Yilmaz');
$teacher2 = $mkuser('teacher2', 'Mehmet', 'Kaya');
$students = [];
foreach ([['Ali', 'Demir'], ['Zeynep', 'Celik'], ['Can', 'Sahin'], ['Elif', 'Arslan'], ['Emre', 'Dogan']] as $i => $n) {
    $students[$i + 1] = $mkuser('student' . ($i + 1), $n[0], $n[1]);
}

// Enrolments (manual enrolment method, created by default on every course).
$gen->enrol_user($teacher1->id, $cs101->id, 'editingteacher');
$gen->enrol_user($teacher1->id, $cs201->id, 'editingteacher');
$gen->enrol_user($teacher2->id, $math101->id, 'editingteacher');
foreach ($students as $s) {
    $gen->enrol_user($s->id, $cs101->id, 'student');
}
// student3 is enrolled in CS201 although CS101 is not completed: Moodle does not block this (MDL-22648).
foreach ([1, 2, 3] as $i) {
    $gen->enrol_user($students[$i]->id, $cs201->id, 'student');
}
foreach ([4, 5] as $i) {
    $gen->enrol_user($students[$i]->id, $math101->id, 'student');
}

// Assignment in CS101: completed when the student receives a passing grade (>= 50).
$assignrec = $gen->create_module('assign', [
    'course' => $cs101->id,
    'name' => 'Assignment 1',
    'intro' => 'Write a program that prints the first 20 Fibonacci numbers.',
    'allowsubmissionsfromdate' => $start,
    'duedate' => make_timestamp(2026, 11, 15, 23, 59),
    'cutoffdate' => make_timestamp(2026, 11, 22, 23, 59),
    'grade' => 100,
    'gradepass' => 50,
    'assignsubmission_onlinetext_enabled' => 1,
    'assignsubmission_file_enabled' => 1,
    'assignsubmission_file_maxfiles' => 1,
    'assignsubmission_file_maxsizebytes' => 1048576,
    'completion' => COMPLETION_TRACKING_AUTOMATIC,
    'completionusegrade' => 1,
    'completionpassgrade' => 1,
]);

// Course completion rules.
// CS101 is complete when Assignment 1 is complete.
$criterion = completion_criteria::factory(['criteriatype' => COMPLETION_CRITERIA_TYPE_ACTIVITY]);
$data = (object) ['id' => $cs101->id, 'criteria_activity' => [$assignrec->cmid => 1]];
$criterion->update_config($data);
// CS201 requires completion of CS101 ("Completion of other courses" criterion) AND the teacher marking
// the student complete. Without the second criterion, completing CS101 would also complete CS201.
$criterion = completion_criteria::factory(['criteriatype' => COMPLETION_CRITERIA_TYPE_COURSE]);
$data = (object) ['id' => $cs201->id, 'criteria_course' => [$cs101->id]];
$criterion->update_config($data);
$criterion = completion_criteria::factory(['criteriatype' => COMPLETION_CRITERIA_TYPE_ROLE]);
$teacherroleid = $DB->get_field('role', 'id', ['shortname' => 'editingteacher'], MUST_EXIST);
$data = (object) ['id' => $cs201->id, 'criteria_role' => [$teacherroleid => 1]];
$criterion->update_config($data);

// Submissions and grades.
$assigngen = $gen->get_plugin_generator('mod_assign');
$cm = get_coursemodule_from_id('assign', $assignrec->cmid, 0, false, MUST_EXIST);
$assign = new assign(context_module::instance($cm->id), $cm, $cs101);
$grades = [1 => 85, 2 => 72, 3 => 40];
foreach ([1, 2, 3, 4] as $i) {
    $assigngen->create_submission([
        'userid' => $students[$i]->id,
        'cmid' => $assignrec->cmid,
        'onlinetext' => '<p>Submission of student' . $i . '</p>',
        'status' => ASSIGN_SUBMISSION_STATUS_SUBMITTED,
    ]);
}
\core\session\manager::set_user($teacher1);
foreach ($grades as $i => $value) {
    $grade = $assign->get_user_grade($students[$i]->id, true);
    $grade->grade = $value;
    $grade->grader = $teacher1->id;
    $assign->update_grade($grade);
}
\core\session\manager::set_user(get_admin());

// Mark CS101 complete for students who passed, so the state does not depend on cron timing.
foreach ($grades as $i => $value) {
    if ($value >= 50) {
        $ccompletion = new completion_completion(['course' => $cs101->id, 'userid' => $students[$i]->id]);
        $ccompletion->mark_enrolled();
        $ccompletion->mark_complete();
    }
}

$transaction->allow_commit();

echo "Sample data loaded.\n";
printf("  Category: %s (id %d)\n", $category->name, $category->id);
foreach ([$cs101, $cs201, $math101] as $c) {
    printf("  Course:   %-8s id %d  %s\n", $c->shortname, $c->id, $c->fullname);
}
echo "  Users:    teacher1, teacher2, student1..student5 (password = SAMPLE_USER_PASSWORD)\n";
echo "  CS101 completed by: student1, student2. CS201 requires CS101.\n";
