<?php
// Checks the grading rules of docs/srs/modules/grading.md against the Docker Moodle 5.3.1 (infra/) and prints PASS/FAIL per rule.
// It creates a temporary course "GRDTEST-..." with the sample users student1/student2, and deletes it again at the end.
//
// Run from the repository root (Moodle running, sample data loaded):
//   docker compose -f infra/docker-compose.yml exec -T -u www-data moodle php < docs/srs/modules/grading/verify_rules.php
// (PowerShell: Get-Content docs/srs/modules/grading/verify_rules.php | docker compose -f infra/docker-compose.yml exec -T -u www-data moodle php)
define('CLI_SCRIPT', true);
require('/var/www/moodle/config.php');
require_once($CFG->libdir . '/gradelib.php');
require_once($CFG->dirroot . '/course/lib.php');
require_once($CFG->dirroot . '/enrol/manual/locallib.php');

\core\session\manager::set_user(get_admin());

$failures = 0;
function check(string $id, string $what, $expected, $actual): void {
    global $failures;
    $same = is_float($expected) || is_float($actual) ? abs((float) $expected - (float) $actual) < 0.005 : $expected === $actual;
    $failures += $same ? 0 : 1;
    printf("%-4s %-4s %-78s expected %-12s actual %s\n", $same ? 'PASS' : 'FAIL', $id, $what,
        is_float($expected) ? round($expected, 2) : var_export($expected, true),
        is_float($actual) ? round($actual, 2) : var_export($actual, true));
}

function course_total(int $courseid, int $userid): ?float {
    grade_regrade_final_grades($courseid);
    $courseitem = grade_item::fetch_course_item($courseid);
    $grade = grade_grade::fetch(['itemid' => $courseitem->id, 'userid' => $userid]);
    return $grade && $grade->finalgrade !== null ? (float) $grade->finalgrade : null;
}

function configure(grade_category $category, int $aggregation, int $onlygraded, int $droplow): void {
    $category->aggregation = $aggregation;
    $category->aggregateonlygraded = $onlygraded;
    $category->droplow = $droplow;
    $category->update();
}

$student1 = $DB->get_record('user', ['username' => 'student1'], '*', MUST_EXIST);
$student2 = $DB->get_record('user', ['username' => 'student2'], '*', MUST_EXIST);

$course = create_course((object) ['fullname' => 'Grading rules check', 'shortname' => 'GRDTEST-' . time(), 'category' => 1]);
try {
    $manual = enrol_get_plugin('manual');
    $instance = $DB->get_record('enrol', ['courseid' => $course->id, 'enrol' => 'manual'], '*', MUST_EXIST);
    $studentrole = $DB->get_record('role', ['shortname' => 'student'], '*', MUST_EXIST);
    $manual->enrol_user($instance, $student1->id, $studentrole->id);
    $manual->enrol_user($instance, $student2->id, $studentrole->id);

    $category = grade_category::fetch_course_category($course->id);
    $items = [];
    foreach (['A' => 1.0, 'B' => 3.0, 'C' => 2.0] as $name => $weight) {   // name => weight (aggregationcoef)
        $item = new grade_item(['courseid' => $course->id, 'categoryid' => $category->id, 'itemtype' => 'manual',
            'itemname' => 'Item ' . $name, 'gradetype' => GRADE_TYPE_VALUE, 'grademin' => 0, 'grademax' => 100,
            'aggregationcoef' => $weight], false);
        $item->insert();
        $items[$name] = $item;
    }
    $items['A']->update_final_grade($student1->id, 80, 'verify');
    $items['B']->update_final_grade($student1->id, 40, 'verify');
    // Item C stays empty for student1. Weights: A=1, B=3, C=2.

    echo "Items for student1: A=80 (weight 1), B=40 (weight 3), C=empty (weight 2); all out of 100\n\n";

    echo "-- Aggregation and empty grades\n";
    configure($category, GRADE_AGGREGATE_WEIGHTED_MEAN, 1, 0);
    check('G1', 'Weighted mean, empty grade excluded: (80*1 + 40*3) / (1+3)', 50.0, course_total($course->id, $student1->id));
    configure($category, GRADE_AGGREGATE_WEIGHTED_MEAN, 0, 0);
    check('G2', 'Weighted mean, empty grade counted as 0: (80*1 + 40*3 + 0*2) / 6', 200 / 6, course_total($course->id, $student1->id));
    configure($category, GRADE_AGGREGATE_MEAN, 1, 0);
    check('G3', 'Mean of grades, empty excluded: (80 + 40) / 2', 60.0, course_total($course->id, $student1->id));
    configure($category, GRADE_AGGREGATE_MEAN, 0, 0);
    check('G4', 'Mean of grades, empty counted as 0: (80 + 40 + 0) / 3', 40.0, course_total($course->id, $student1->id));
    check('G5', 'Student2 has no grade at all: total is empty (null)', null, course_total($course->id, $student2->id));

    echo "\n-- Drop lowest\n";
    configure($category, GRADE_AGGREGATE_MEAN, 1, 1);
    check('G6', 'Mean, empty excluded, drop lowest 1 (drops 40): 80', 80.0, course_total($course->id, $student1->id));
    configure($category, GRADE_AGGREGATE_MEAN, 0, 1);
    check('G7', 'Mean, empty counted as 0, drop lowest 1 (drops the 0): (80 + 40) / 2', 60.0, course_total($course->id, $student1->id));
    $category->droplow = 1;
    $category->keephigh = 2;
    $category->update();
    $reloaded = grade_category::fetch(['id' => $category->id]);
    check('G8', 'Drop lowest and keep highest cannot both be set (keephigh is reset to 0)', 0, (int) $reloaded->keephigh);
    configure($category, GRADE_AGGREGATE_WEIGHTED_MEAN, 1, 0);

    echo "\n-- Invalid input\n";
    $items['A']->update_final_grade($student1->id, 150, 'verify');
    $grade = grade_grade::fetch(['itemid' => $items['A']->id, 'userid' => $student1->id]);
    check('G9', 'Grade 150 for an item with maximum 100 is saved as the maximum', 100.0, (float) $grade->finalgrade);
    $items['A']->update_final_grade($student1->id, -5, 'verify');
    $grade = grade_grade::fetch(['itemid' => $items['A']->id, 'userid' => $student1->id]);
    check('G10', 'Grade -5 for an item with minimum 0 is saved as the minimum', 0.0, (float) $grade->finalgrade);
    $items['A']->update_final_grade($student1->id, 80, 'verify');

    echo "\n-- What a student can see (gradereport_user_get_grade_items, called as the student)\n";
    $visible = function (stdClass $viewer, int $subject) use ($course): array {
        global $COURSE, $SITE;
        $COURSE = $SITE; // validate_context() keeps the course record in $COURSE; reset it so changed course settings are read again.
        \core\session\manager::set_user($viewer);
        $result = \gradereport_user\external\user::get_grade_items($course->id, $subject);
        \core\session\manager::set_user(get_admin());
        $names = [];
        foreach ($result['usergrades'][0]['gradeitems'] as $gi) {
            $names[] = trim($gi['itemname'] ?? ($gi['itemtype'] ?? '')) . '=' . trim(strip_tags($gi['gradeformatted']));
        }
        return $names;
    };
    $own = $visible($student1, $student1->id);
    check('G11', 'Own grades are visible (Item A, B, C and the course total)', 4, count($own));
    check('G12', 'Own grade of Item A as the student sees it', 'Item A=80.00', $own[0]);

    $items['B']->set_hidden(1);
    grade_regrade_final_grades($course->id);
    $withhidden = $visible($student1, $student1->id);
    echo '     (student sees: ' . implode('; ', $withhidden) . "; setting grade_report_user_showtotalsifcontainhidden = "
        . var_export(get_config('moodle', 'grade_report_user_showtotalsifcontainhidden'), true) . ")
";
    check('G13', 'Hidden item B is not shown to the student', false, in_array('Item B=40.00', $withhidden, true));
    check('G14', 'Web service: the course total still includes the hidden item (50, not 80)', true,
        in_array('course=50.00', $withhidden, true));
    $items['B']->set_hidden(0);

    echo "\n-- Unauthorized access\n";
    $denied = function (stdClass $viewer, int $subject) use ($course): string {
        global $COURSE, $SITE;
        $COURSE = $SITE;
        \core\session\manager::set_user($viewer);
        try {
            \gradereport_user\external\user::get_grade_items($course->id, $subject);
            return 'allowed';
        } catch (moodle_exception $e) {
            return $e->errorcode;
        } finally {
            \core\session\manager::set_user(get_admin());
        }
    };
    check('G15', 'student1 asks for the grades of student2', 'nopermissiontoviewgrades', $denied($student1, $student2->id));
    check('G16', 'student1 asks for all grades of the course (userid 0)', 'nopermissions', $denied($student1, 0));
    $DB->set_field('course', 'showgrades', 0, ['id' => $course->id]);
    check('G17', 'Course setting "Show gradebook to students" off: student1 asks for own grades', 'nopermissiontoviewgrades', $denied($student1, $student1->id));
    $DB->set_field('course', 'showgrades', 1, ['id' => $course->id]);
    $coursecontext = context_course::instance($course->id);
    $caps = (has_capability('moodle/grade:edit', $coursecontext, $student1) ? 'allowed' : 'denied') . ' / '
        . (has_capability('moodle/grade:viewall', $coursecontext, $student1) ? 'allowed' : 'denied');
    check('G18', 'A student has moodle/grade:edit and moodle/grade:viewall? (edit / viewall)', 'denied / denied', $caps);
} finally {
    delete_course($course->id, false);
    echo "\nTemporary course deleted.\n";
}
echo $failures ? "\n$failures check(s) FAILED\n" : "\nAll checks passed\n";
