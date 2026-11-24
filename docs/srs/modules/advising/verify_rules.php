<?php
// Verifies the REQ-ADV business rules against a running Moodle 5.3.1 (the Docker setup from #8).
// Calls the real Moodle functions, prints one line per scenario, and rolls everything back at the end.
//
// Run (from the repository root, Moodle running, sample data loaded):
//   docker compose -f infra/docker-compose.yml cp docs/srs/modules/advising/verify_rules.php moodle:/tmp/verify_rules.php
//   docker compose -f infra/docker-compose.yml exec -T -u www-data moodle php /tmp/verify_rules.php

define('CLI_SCRIPT', true);
require('/var/www/moodle/config.php');
require_once($CFG->libdir . '/completionlib.php');
require_once($CFG->libdir . '/enrollib.php');
require_once($CFG->libdir . '/gradelib.php');
require_once($CFG->dirroot . '/completion/criteria/completion_criteria_course.php');
require_once($CFG->dirroot . '/completion/completion_completion.php');

use availability_date\condition as date_condition;
use core_availability\tree;

$results = [];
$failures = 0;

/**
 * Run one scenario. $check gets the return value of $action and returns [bool $pass, string $actual].
 */
function scenario(string $req, string $kind, string $desc, callable $action, callable $check): void {
    global $results, $failures;
    try {
        [$pass, $actual] = $check($action());
    } catch (\Throwable $e) {
        $pass = false;
        $actual = 'exception ' . get_class($e) . ': ' . $e->getMessage();
    }
    if (!$pass) {
        $failures++;
    }
    $actual = trim(preg_replace('/\s+/', ' ', strip_tags($actual)));
    $results[] = sprintf("%-4s | %-10s | %-12s | %s | actual: %s", $pass ? 'PASS' : 'FAIL', $req, $kind, $desc, $actual);
}

/** Expect is_available() to return $expected; also report the restriction text Moodle would show. */
function expect_available(bool $expected): callable {
    return fn($r) => [$r[0] === $expected, ($r[0] ? 'available' : 'not available') . ($r[1] !== '' ? " ({$r[1]})" : '')];
}

$admin = get_admin();
\core\session\manager::set_user($admin);
set_config('enableavailability', 1);
$transaction = $DB->start_delegated_transaction();
$gen = \core\test\phpunit\phpunit_util::get_data_generator();

$cs101 = $DB->get_record('course', ['shortname' => 'CS101'], '*', MUST_EXIST);
$cs201 = $DB->get_record('course', ['shortname' => 'CS201'], '*', MUST_EXIST);
$u = [];
foreach (['student1', 'student2', 'student3', 'student4', 'student5', 'teacher1'] as $name) {
    $u[$name] = $DB->get_record('user', ['username' => $name], '*', MUST_EXIST);
}
// Sample data: Assignment 1 in CS101, grade out of 100, pass grade 50.
// student1 = 85 (complete, passed), student2 = 72 (complete, passed), student3 = 40 (complete, failed),
// student4 submitted but not graded, student5 nothing.
$assigncm = $DB->get_record_sql(
    "SELECT cm.* FROM {course_modules} cm JOIN {modules} m ON m.id = cm.module JOIN {assign} a ON a.id = cm.instance
      WHERE m.name = 'assign' AND a.course = ? AND a.name = 'Assignment 1'", [$cs101->id], MUST_EXIST);
$gradeitem = grade_item::fetch(['itemtype' => 'mod', 'itemmodule' => 'assign', 'iteminstance' => $assigncm->instance,
    'courseid' => $cs101->id]);

/**
 * Create a page in CS101 with the given availability tree (JSON object) and return its cm id.
 */
function restricted_page(string $name, ?stdClass $tree): int {
    global $gen, $cs101;
    $page = $gen->create_module('page', ['course' => $cs101->id, 'name' => $name,
        'availability' => $tree ? json_encode($tree) : null]);
    rebuild_course_cache($cs101->id, true);
    return (int) $page->cmid;
}

/** [is_available, restriction text] of a course module for a user, as Moodle evaluates it. */
function check_cm(int $cmid, stdClass $user): array {
    global $cs101;
    get_fast_modinfo($cs101->id, $user->id, true);
    $cm = get_fast_modinfo($cs101->id, $user->id)->get_cm($cmid);
    $info = new \core_availability\info_module($cm);
    $text = '';
    $available = $info->is_available($text, false, $user->id);
    if ($text !== '') {
        $text = \core_availability\info::format_info($text, $cs101);
    }
    return [$available, (string) $text];
}

$completion = fn(int $cmid, int $state) => \availability_completion\condition::get_json($cmid, $state);
$past = time() - DAYSECS;

// ---------------------------------------------------------------- REQ-ADV-01 restriction tree
$cm = restricted_page('ADV-01 no restriction', null);
scenario('REQ-ADV-01', 'normal', 'no restriction: available to student5', fn() => check_cm($cm, $u['student5']), expect_available(true));

$cm = restricted_page('ADV-01 AND', tree::get_root_json([
    $completion($assigncm->id, COMPLETION_COMPLETE), date_condition::get_json(date_condition::DIRECTION_FROM, $past)], tree::OP_AND));
scenario('REQ-ADV-01', 'normal', 'AND(Assignment 1 complete, from yesterday): student1', fn() => check_cm($cm, $u['student1']), expect_available(true));
scenario('REQ-ADV-01', 'normal', 'AND(Assignment 1 complete, from yesterday): student3', fn() => check_cm($cm, $u['student3']), expect_available(false));

$cm = restricted_page('ADV-01 OR', tree::get_root_json([
    \availability_grade\condition::get_json($gradeitem->id, 80), $completion($assigncm->id, COMPLETION_INCOMPLETE)], tree::OP_OR));
scenario('REQ-ADV-01', 'normal', 'OR(grade >= 80, Assignment 1 incomplete): student1 (85)', fn() => check_cm($cm, $u['student1']), expect_available(true));
scenario('REQ-ADV-01', 'normal', 'OR(grade >= 80, Assignment 1 incomplete): student2 (72, passed)', fn() => check_cm($cm, $u['student2']), expect_available(false));
scenario('REQ-ADV-01', 'boundary', 'OR(...): student3 (40, failed counts as incomplete)', fn() => check_cm($cm, $u['student3']), expect_available(true));

$cm = restricted_page('ADV-01 NOT', tree::get_root_json([$completion($assigncm->id, COMPLETION_COMPLETE)], tree::OP_NOT_AND));
scenario('REQ-ADV-01', 'boundary', 'NOT(Assignment 1 complete): student1', fn() => check_cm($cm, $u['student1']), expect_available(false));
scenario('REQ-ADV-01', 'boundary', 'NOT(Assignment 1 complete): student4', fn() => check_cm($cm, $u['student4']), expect_available(true));

scenario('REQ-ADV-01', 'invalid', 'tree with unknown operator is rejected', function() {
    try {
        new tree((object) ['op' => 'xor', 'c' => [], 'show' => true]);
        return 'accepted';
    } catch (coding_exception $e) {
        return 'coding_exception: ' . $e->getMessage();
    }
}, fn($r) => [str_contains($r, 'unknown ->op'), $r]);

// ---------------------------------------------------------------- REQ-ADV-02 activity completion condition
$cm = restricted_page('ADV-02 complete', tree::get_root_json([$completion($assigncm->id, COMPLETION_COMPLETE)]));
scenario('REQ-ADV-02', 'normal', 'requires complete: student1 (complete and passed)', fn() => check_cm($cm, $u['student1']), expect_available(true));
scenario('REQ-ADV-02', 'boundary', 'requires complete: student3 (complete but failed)', fn() => check_cm($cm, $u['student3']), expect_available(false));
scenario('REQ-ADV-02', 'boundary', 'requires complete: student4 (submitted, not graded)', fn() => check_cm($cm, $u['student4']), expect_available(false));

$cm = restricted_page('ADV-02 fail', tree::get_root_json([$completion($assigncm->id, COMPLETION_COMPLETE_FAIL)]));
scenario('REQ-ADV-02', 'normal', 'requires complete and failed: student3', fn() => check_cm($cm, $u['student3']), expect_available(true));
scenario('REQ-ADV-02', 'normal', 'requires complete and failed: student1', fn() => check_cm($cm, $u['student1']), expect_available(false));

$cm = restricted_page('ADV-02 missing', tree::get_root_json([$completion(999999, COMPLETION_COMPLETE)]));
scenario('REQ-ADV-02', 'invalid', 'condition on a missing activity: student1', fn() => check_cm($cm, $u['student1']), expect_available(false));
$cm = restricted_page('ADV-02 missing NOT', tree::get_root_json([$completion(999999, COMPLETION_COMPLETE)], tree::OP_NOT_AND));
scenario('REQ-ADV-02', 'invalid', 'NOT(condition on a missing activity): still not available', fn() => check_cm($cm, $u['student1']), expect_available(false));

// ---------------------------------------------------------------- REQ-ADV-03 date condition
$t = make_timestamp(2026, 12, 1, 9, 0);
$from = restricted_page('ADV-03 from', tree::get_root_json([date_condition::get_json(date_condition::DIRECTION_FROM, $t)]));
$until = restricted_page('ADV-03 until', tree::get_root_json([date_condition::get_json(date_condition::DIRECTION_UNTIL, $t)]));
foreach ([[$from, $t, true, 'from T at T'], [$from, $t - 1, false, 'from T at T-1s'],
          [$until, $t - 1, true, 'until T at T-1s'], [$until, $t, false, 'until T at T']] as [$c, $now, $exp, $label]) {
    scenario('REQ-ADV-03', $now === $t ? 'boundary' : 'normal', $label, function() use ($c, $now, $u) {
        date_condition::set_current_time_for_test($now);
        $r = check_cm($c, $u['student1']);
        date_condition::set_current_time_for_test(0);
        return $r;
    }, expect_available($exp));
}

// ---------------------------------------------------------------- REQ-ADV-04 grade condition
$min72 = restricted_page('ADV-04 min 72', tree::get_root_json([\availability_grade\condition::get_json($gradeitem->id, 72)]));
$max72 = restricted_page('ADV-04 max 72', tree::get_root_json([\availability_grade\condition::get_json($gradeitem->id, null, 72)]));
$min0 = restricted_page('ADV-04 min 0', tree::get_root_json([\availability_grade\condition::get_json($gradeitem->id, 0)]));
scenario('REQ-ADV-04', 'normal', 'grade >= 72%: student1 (85)', fn() => check_cm($min72, $u['student1']), expect_available(true));
scenario('REQ-ADV-04', 'boundary', 'grade >= 72%: student2 (exactly 72)', fn() => check_cm($min72, $u['student2']), expect_available(true));
scenario('REQ-ADV-04', 'boundary', 'grade < 72%: student2 (exactly 72)', fn() => check_cm($max72, $u['student2']), expect_available(false));
scenario('REQ-ADV-04', 'normal', 'grade < 72%: student3 (40)', fn() => check_cm($max72, $u['student3']), expect_available(true));
scenario('REQ-ADV-04', 'invalid', 'grade >= 0%: student4 has no grade', fn() => check_cm($min0, $u['student4']), expect_available(false));

// ---------------------------------------------------------------- REQ-ADV-05 course completion
$info201 = new completion_info($cs201);
scenario('REQ-ADV-05', 'normal', 'CS201 overall aggregation method', fn() => $info201->get_aggregation_method(),
    fn($m) => [(int) $m === COMPLETION_AGGREGATION_ALL, $m == COMPLETION_AGGREGATION_ALL ? 'ALL' : 'ANY']);
$coursecrit = $DB->get_record('course_completion_criteria',
    ['course' => $cs201->id, 'criteriatype' => COMPLETION_CRITERIA_TYPE_COURSE], '*', MUST_EXIST);
$crit = new completion_criteria_course((array) $coursecrit, false);
foreach ([['student1', true], ['student3', false]] as [$name, $exp]) {
    scenario('REQ-ADV-05', $exp ? 'normal' : 'boundary', "\"Completion of other courses: CS101\" met for $name",
        function() use ($crit, $cs201, $u, $name) {
            $cc = new completion_criteria_completion(['userid' => $u[$name]->id, 'course' => $cs201->id,
                'criteriaid' => $crit->id], false);
            return $crit->review($cc, false);
        }, fn($r) => [$r === $exp, $r ? 'met' : 'not met']);
}
scenario('REQ-ADV-05', 'boundary', 'CS201 not complete for student1: CS101 done, teacher has not marked it',
    fn() => $info201->is_course_complete($u['student1']->id), fn($r) => [$r === false, $r ? 'complete' : 'not complete']);
scenario('REQ-ADV-05', 'invalid', 'course completion disabled: no criteria evaluated', function() use ($cs201) {
    $c = clone $cs201;
    $c->enablecompletion = 0;
    return (new completion_info($c))->is_enabled();
}, fn($r) => [!$r, $r ? 'enabled' : 'disabled']);

// ---------------------------------------------------------------- REQ-ADV-06 what the student sees
$shown = restricted_page('ADV-06 shown', tree::get_root_json([$completion($assigncm->id, COMPLETION_COMPLETE)], tree::OP_AND, true));
$hidden = restricted_page('ADV-06 hidden', tree::get_root_json([$completion($assigncm->id, COMPLETION_COMPLETE)], tree::OP_AND, false));
scenario('REQ-ADV-06', 'normal', 'shown restriction: student3 sees the name and the reason', function() use ($shown, $u, $cs101) {
    get_fast_modinfo($cs101->id, $u['student3']->id, true);
    $cm = get_fast_modinfo($cs101->id, $u['student3']->id)->get_cm($shown);
    return [$cm->uservisible, $cm->is_visible_on_course_page(), (string) \core_availability\info::format_info($cm->availableinfo, $cs101)];
}, fn($r) => [!$r[0] && $r[1] && str_contains($r[2], 'Not available unless'),
    'uservisible=' . var_export($r[0], true) . ', on course page=' . var_export($r[1], true) . ", text: {$r[2]}"]);
scenario('REQ-ADV-06', 'boundary', 'hidden restriction (eye icon): student3 does not see the item at all', function() use ($hidden, $u, $cs101) {
    get_fast_modinfo($cs101->id, $u['student3']->id, true);
    $cm = get_fast_modinfo($cs101->id, $u['student3']->id)->get_cm($hidden);
    return [$cm->uservisible, $cm->is_visible_on_course_page(), (string) $cm->availableinfo];
}, fn($r) => [!$r[0] && !$r[1] && $r[2] === '',
    'uservisible=' . var_export($r[0], true) . ', on course page=' . var_export($r[1], true) . ", text: '{$r[2]}'"]);

// ---------------------------------------------------------------- REQ-ADV-07 who may bypass restrictions
$ctx = context_course::instance($cs101->id);
scenario('REQ-ADV-07', 'normal', 'teacher1 has moodle/course:ignoreavailabilityrestrictions in CS101',
    fn() => has_capability('moodle/course:ignoreavailabilityrestrictions', $ctx, $u['teacher1']), fn($r) => [$r === true, var_export($r, true)]);
scenario('REQ-ADV-07', 'unauthorized', 'student3 does not have moodle/course:ignoreavailabilityrestrictions',
    fn() => has_capability('moodle/course:ignoreavailabilityrestrictions', $ctx, $u['student3']), fn($r) => [$r === false, var_export($r, true)]);
scenario('REQ-ADV-07', 'normal', 'teacher1 can open the hidden restricted item', function() use ($hidden, $u, $cs101) {
    get_fast_modinfo($cs101->id, $u['teacher1']->id, true);
    return get_fast_modinfo($cs101->id, $u['teacher1']->id)->get_cm($hidden)->uservisible;
}, fn($r) => [$r === true, 'uservisible=' . var_export($r, true)]);
scenario('REQ-ADV-07', 'unauthorized', 'student3 cannot open the restricted item by its URL (require_login)', function() use ($shown, $u, $cs101) {
    \core\session\manager::set_user($u['student3']);
    try {
        $cm = get_coursemodule_from_id('page', $shown, 0, false, MUST_EXIST);
        require_login($cs101, false, $cm, false, true);
        return 'opened';
    } catch (require_login_exception $e) {
        return 'require_login_exception: ' . $e->getMessage();
    } finally {
        \core\session\manager::set_user(get_admin());
    }
}, fn($r) => [str_starts_with($r, 'require_login_exception'), $r]);

// ---------------------------------------------------------------- REQ-ADV-08 gap: no course-level prerequisite (MDL-22648)
$info101 = new completion_info($cs101);
scenario('REQ-ADV-08', 'gap', 'student3 is enrolled in CS201 although CS101 is not complete', function() use ($info101, $cs201, $u) {
    return [is_enrolled(context_course::instance($cs201->id), $u['student3']), $info101->is_course_complete($u['student3']->id)];
}, fn($r) => [$r[0] && !$r[1], 'enrolled in CS201=' . var_export($r[0], true) . ', CS101 complete=' . var_export($r[1], true)]);
scenario('REQ-ADV-08', 'gap', 'self enrolment into CS201 is offered to student4 (CS101 not complete)', function() use ($cs201, $u, $DB) {
    $plugin = enrol_get_plugin('self');
    $instance = $DB->get_record('enrol', ['courseid' => $cs201->id, 'enrol' => 'self'], '*', MUST_EXIST);
    $plugin->update_status($instance, ENROL_INSTANCE_ENABLED);
    $instance = $DB->get_record('enrol', ['id' => $instance->id]);
    \core\session\manager::set_user($u['student4']);
    try {
        return $plugin->can_self_enrol($instance, false);
    } finally {
        \core\session\manager::set_user(get_admin());
    }
}, fn($r) => [$r === true, $r === true ? 'can self enrol' : "refused: $r"]);
scenario('REQ-ADV-08', 'gap', 'no core availability condition can refer to another course', function() {
    return array_keys(core_component::get_plugin_list('availability'));
}, fn($r) => [!in_array('course', $r) && !in_array('coursecompletion', $r), 'installed conditions: ' . implode(', ', $r)]);

// ---------------------------------------------------------------- Output and rollback
$passed = count($results) - $failures;
echo "REQ-ADV verification against Moodle {$CFG->release}\n";
echo str_repeat('-', 100) . "\n";
echo implode("\n", $results) . "\n";
echo str_repeat('-', 100) . "\n";
printf("%d scenarios: %d passed, %d failed\n", count($results), $passed, $failures);

try {
    $transaction->rollback(new Exception('rollback'));
} catch (Exception $e) {
    // Expected: rollback() rethrows the exception after rolling back.
}
purge_caches();
exit($failures === 0 ? 0 : 1);
