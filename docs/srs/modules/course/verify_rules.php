<?php
// Verifies the REQ-CRS business rules against a running Moodle 5.3.1 (the Docker setup from #8).
// Calls the real Moodle functions, prints one line per scenario, and rolls everything back at the end.
//
// Run (from the repository root, Moodle running, sample data loaded):
//   docker compose -f infra/docker-compose.yml cp docs/srs/modules/course/verify_rules.php moodle:/tmp/verify_rules.php
//   docker compose -f infra/docker-compose.yml exec -T -u www-data moodle php /tmp/verify_rules.php

define('CLI_SCRIPT', true);
require('/var/www/moodle/config.php');
require_once($CFG->dirroot . '/course/lib.php');
require_once($CFG->libdir . '/enrollib.php');

$results = [];
$failures = 0;

/**
 * Run one scenario. $expected is 'ok', an exception error code / class, or a callable that checks the return value.
 */
function scenario(string $req, string $kind, string $desc, callable $action, $expected): void {
    global $results, $failures;
    try {
        $value = $action();
        if (is_callable($expected)) {
            [$pass, $actual] = $expected($value);
        } else {
            $pass = ($expected === 'ok');
            $actual = 'ok';
        }
    } catch (\Throwable $e) {
        $code = ($e instanceof moodle_exception) ? $e->errorcode : get_class($e);
        $actual = 'exception ' . $code;
        $pass = (!is_callable($expected)
            && ($expected === $code || (class_exists($expected) && $e instanceof $expected)));
    }
    if (!$pass) {
        $failures++;
    }
    $actual = trim(preg_replace('/\s+/', ' ', $actual));
    $results[] = sprintf("%-4s | %-10s | %-14s | %s | expected: %s | actual: %s",
        $pass ? 'PASS' : 'FAIL', $req, $kind, $desc, is_callable($expected) ? 'see check' : $expected, $actual);
}

$admin = get_admin();
\core\session\manager::set_user($admin);
$transaction = $DB->start_delegated_transaction();

$eng = core_course_category::get($DB->get_field('course_categories', 'id', ['idnumber' => 'FIG-ENG'], MUST_EXIST));
$cs101 = $DB->get_record('course', ['shortname' => 'CS101'], '*', MUST_EXIST);
$student1 = $DB->get_record('user', ['username' => 'student1'], '*', MUST_EXIST);
$student4 = $DB->get_record('user', ['username' => 'student4'], '*', MUST_EXIST);
$teacher1 = $DB->get_record('user', ['username' => 'teacher1'], '*', MUST_EXIST);
$t0 = make_timestamp(2027, 2, 1);
$base = ['category' => $eng->id, 'fullname' => 'Verify course', 'startdate' => $t0];

// ---------------------------------------------------------------- Courses (REQ-CRS-01..03)
scenario('REQ-CRS-01', 'normal', 'create course with fullname, shortname, category', function() use ($base) {
    return create_course((object) ($base + ['shortname' => 'VER-1']));
}, fn($c) => [$c->id > 0, "created id {$c->id}"]);

scenario('REQ-CRS-01', 'invalid', 'duplicate shortname CS101', function() use ($base) {
    return create_course((object) ($base + ['shortname' => 'CS101']));
}, 'shortnametaken');

scenario('REQ-CRS-01', 'boundary', 'shortname differing only in case (cs101)', function() use ($base) {
    return create_course((object) ($base + ['shortname' => 'cs101']));
}, fn($c) => [$c->id > 0, 'created: uniqueness check is case-sensitive']);

scenario('REQ-CRS-01', 'invalid', 'duplicate course idnumber CS101', function() use ($base) {
    return create_course((object) ($base + ['shortname' => 'VER-2', 'idnumber' => 'CS101']));
}, 'courseidnumbertaken');

scenario('REQ-CRS-01', 'boundary', 'empty idnumber twice is allowed', function() use ($base) {
    create_course((object) ($base + ['shortname' => 'VER-3', 'idnumber' => '']));
    return create_course((object) ($base + ['shortname' => 'VER-4', 'idnumber' => '']));
}, fn($c) => [$c->id > 0, 'created']);

scenario('REQ-CRS-01', 'invalid', 'category does not exist', function() use ($base) {
    return create_course((object) (['category' => 999999, 'shortname' => 'VER-5'] + $base));
}, 'dml_missing_record_exception');

scenario('REQ-CRS-02', 'invalid', 'end date before start date', function() use ($base, $t0) {
    return create_course((object) ($base + ['shortname' => 'VER-6', 'enddate' => $t0 - 1]));
}, 'enddatebeforestartdate');

scenario('REQ-CRS-02', 'boundary', 'end date equal to start date', function() use ($base, $t0) {
    return create_course((object) ($base + ['shortname' => 'VER-7', 'enddate' => $t0]));
}, fn($c) => [$c->id > 0, 'created']);

scenario('REQ-CRS-02', 'invalid', 'end date without start date', function() use ($base, $t0) {
    return create_course((object) (['startdate' => 0, 'shortname' => 'VER-8', 'enddate' => $t0] + $base));
}, 'nostartdatenoenddate');

scenario('REQ-CRS-02', 'normal', 'no end date (0) is allowed', function() use ($base) {
    return create_course((object) ($base + ['shortname' => 'VER-9', 'enddate' => 0]));
}, fn($c) => [$c->id > 0 && (int) $c->enddate === 0, 'created, enddate 0']);

scenario('REQ-CRS-03', 'normal', 'new course gets default enrolment methods', function() use ($base, $DB) {
    $c = create_course((object) ($base + ['shortname' => 'VER-10']));
    $m = $DB->get_records_menu('enrol', ['courseid' => $c->id], 'sortorder', 'enrol, status');
    return $m;
}, fn($m) => [isset($m['manual']) && (int) $m['manual'] === ENROL_INSTANCE_ENABLED,
    'methods: ' . implode(', ', array_map(fn($k, $v) => $k . ($v ? '(disabled)' : '(enabled)'), array_keys($m), $m))]);

scenario('REQ-CRS-03', 'unauthorized', 'student1 has moodle/course:create in category?', function() use ($eng, $student1) {
    return has_capability('moodle/course:create', context_coursecat::instance($eng->id), $student1);
}, fn($v) => [$v === false, $v ? 'yes' : 'no']);

scenario('REQ-CRS-03', 'unauthorized', 'teacher1 (editingteacher in CS101) has moodle/course:create in category?',
    function() use ($eng, $teacher1) {
        return has_capability('moodle/course:create', context_coursecat::instance($eng->id), $teacher1);
    }, fn($v) => [$v === false, $v ? 'yes' : 'no']);

scenario('REQ-CRS-03', 'normal', 'admin/manager has moodle/course:create in category?', function() use ($eng, $admin) {
    return has_capability('moodle/course:create', context_coursecat::instance($eng->id), $admin);
}, fn($v) => [$v === true, $v ? 'yes' : 'no']);

// ---------------------------------------------------------------- Categories (REQ-CRS-04)
scenario('REQ-CRS-04', 'normal', 'create subcategory under Engineering', function() use ($eng) {
    return core_course_category::create(['name' => 'Verify sub', 'parent' => $eng->id, 'idnumber' => 'VER-CAT-1']);
}, fn($c) => [$c->depth == $eng->depth + 1 && $c->path === $eng->path . '/' . $c->id, "depth {$c->depth}, path {$c->path}"]);

scenario('REQ-CRS-04', 'invalid', 'empty category name', function() {
    return core_course_category::create(['name' => '']);
}, 'categorynamerequired');

scenario('REQ-CRS-04', 'boundary', 'category name of 255 characters', function() {
    return core_course_category::create(['name' => str_repeat('a', 255)]);
}, fn($c) => [$c->id > 0, 'created']);

scenario('REQ-CRS-04', 'boundary', 'category name of 256 characters', function() {
    return core_course_category::create(['name' => str_repeat('a', 256)]);
}, 'categorytoolong');

scenario('REQ-CRS-04', 'invalid', 'duplicate category idnumber FIG-ENG', function() {
    return core_course_category::create(['name' => 'Dup', 'idnumber' => 'FIG-ENG']);
}, 'categoryidnumbertaken');

scenario('REQ-CRS-04', 'normal', 'subcategory of a hidden category is hidden; course in it is hidden', function() use ($base) {
    $hidden = core_course_category::create(['name' => 'Hidden parent', 'visible' => 0]);
    $child = core_course_category::create(['name' => 'Child', 'parent' => $hidden->id]);
    $c = create_course((object) (['category' => $child->id, 'shortname' => 'VER-11'] + $base));
    return [$child->visible, $c->visible];
}, fn($v) => [$v === [0, 0] || $v == [0, 0], "child visible={$v[0]}, course visible={$v[1]}"]);

scenario('REQ-CRS-04', 'invalid', 'move a category into its own subcategory', function() {
    $parent = core_course_category::create(['name' => 'Loop parent']);
    $child = core_course_category::create(['name' => 'Loop child', 'parent' => $parent->id]);
    $parent->change_parent($child->id);
}, 'cannotmovecategory');

scenario('REQ-CRS-04', 'unauthorized', 'teacher1 has moodle/category:manage in Engineering?', function() use ($eng, $teacher1) {
    return has_capability('moodle/category:manage', context_coursecat::instance($eng->id), $teacher1);
}, fn($v) => [$v === false, $v ? 'yes' : 'no']);

// ---------------------------------------------------------------- Manual enrolment (REQ-CRS-05..07)
$manual = enrol_get_plugin('manual');
$course = create_course((object) ($base + ['shortname' => 'VER-ENR']));
$ctx = context_course::instance($course->id);
$minst = $DB->get_record('enrol', ['courseid' => $course->id, 'enrol' => 'manual'], '*', MUST_EXIST);
$studentrole = $DB->get_field('role', 'id', ['shortname' => 'student']);
$editingrole = $DB->get_field('role', 'id', ['shortname' => 'editingteacher']);
$now = time();
$mkuser = function(string $u) use ($DB) {
    return $DB->get_record('user', ['id' => create_user_record($u, 'Verify-pass-1!')->id]);
};

scenario('REQ-CRS-05', 'normal', 'manual enrol as student -> active enrolment + role', function() use ($manual, $minst, $student1, $studentrole, $ctx) {
    $manual->enrol_user($minst, $student1->id, $studentrole);
    return [is_enrolled($ctx, $student1, '', true), user_has_role_assignment($student1->id, $studentrole, $ctx->id)];
}, fn($v) => [$v === [true, true], 'active=' . var_export($v[0], true) . ', role=' . var_export($v[1], true)]);

scenario('REQ-CRS-05', 'boundary', 'enrol the same user twice -> still one user_enrolments row', function() use ($manual, $minst, $student1, $studentrole, $DB) {
    $manual->enrol_user($minst, $student1->id, $studentrole);
    return $DB->count_records('user_enrolments', ['enrolid' => $minst->id, 'userid' => $student1->id]);
}, fn($n) => [$n === 1, "rows=$n"]);

scenario('REQ-CRS-06', 'boundary', 'timestart in the future -> not active yet', function() use ($manual, $minst, $mkuser, $studentrole, $ctx, $now) {
    $u = $mkuser('ver_future');
    $manual->enrol_user($minst, $u->id, $studentrole, $now + DAYSECS);
    return [is_enrolled($ctx, $u, '', false), is_enrolled($ctx, $u, '', true)];
}, fn($v) => [$v === [true, false], 'any=' . var_export($v[0], true) . ', active=' . var_export($v[1], true)]);

scenario('REQ-CRS-06', 'boundary', 'timeend in the past -> not active (expired)', function() use ($manual, $minst, $mkuser, $studentrole, $ctx, $now) {
    $u = $mkuser('ver_expired');
    $manual->enrol_user($minst, $u->id, $studentrole, $now - 2 * DAYSECS, $now - DAYSECS);
    return is_enrolled($ctx, $u, '', true);
}, fn($v) => [$v === false, 'active=' . var_export($v, true)]);

scenario('REQ-CRS-06', 'normal', 'timeend in the future -> active', function() use ($manual, $minst, $mkuser, $studentrole, $ctx, $now) {
    $u = $mkuser('ver_window');
    $manual->enrol_user($minst, $u->id, $studentrole, $now - DAYSECS, $now + DAYSECS);
    return is_enrolled($ctx, $u, '', true);
}, fn($v) => [$v === true, 'active=' . var_export($v, true)]);

scenario('REQ-CRS-06', 'invalid', 'timeend before timestart is accepted by enrol_user (no validation)', function() use ($manual, $minst, $mkuser, $studentrole, $ctx, $now, $DB) {
    $u = $mkuser('ver_reversed');
    $manual->enrol_user($minst, $u->id, $studentrole, $now + 2 * DAYSECS, $now + DAYSECS);
    return [$DB->record_exists('user_enrolments', ['enrolid' => $minst->id, 'userid' => $u->id]), is_enrolled($ctx, $u, '', true)];
}, fn($v) => [$v === [true, false], 'stored=' . var_export($v[0], true) . ', active=' . var_export($v[1], true) . ' (never active)']);

scenario('REQ-CRS-06', 'normal', 'suspended user enrolment -> not active', function() use ($manual, $minst, $mkuser, $studentrole, $ctx) {
    $u = $mkuser('ver_suspended');
    $manual->enrol_user($minst, $u->id, $studentrole, 0, 0, ENROL_USER_SUSPENDED);
    return is_enrolled($ctx, $u, '', true);
}, fn($v) => [$v === false, 'active=' . var_export($v, true)]);

scenario('REQ-CRS-06', 'normal', 'enrolment method disabled -> enrolled users not active', function() use ($manual, $minst, $student1, $ctx) {
    $manual->update_status($minst, ENROL_INSTANCE_DISABLED);
    $active = is_enrolled($ctx, $student1, '', true);
    $manual->update_status($minst, ENROL_INSTANCE_ENABLED);
    return $active;
}, fn($v) => [$v === false, 'active=' . var_export($v, true)]);

scenario('REQ-CRS-05', 'invalid', 'enrol into the site front page course', function() use ($manual, $DB, $student1) {
    $inst = (object) ['id' => 0, 'courseid' => SITEID, 'enrol' => 'manual'];
    $manual->enrol_user($inst, $student1->id);
}, 'coding_exception');

scenario('REQ-CRS-05', 'invalid', 'guest user is never enrolled', function() use ($ctx) {
    return is_enrolled($ctx, guest_user());
}, fn($v) => [$v === false, 'enrolled=' . var_export($v, true)]);

scenario('REQ-CRS-07', 'normal', 'teacher assignment = manual enrolment with role editingteacher; teacher shown as course contact',
    function() use ($manual, $minst, $teacher1, $editingrole, $course) {
        $manual->enrol_user($minst, $teacher1->id, $editingrole);
        $list = new core_course_list_element(get_course($course->id));
        $contacts = $list->get_course_contacts();
        return [isset($contacts[$teacher1->id]), array_map(fn($c) => $c['rolename'] . ': ' . $c['username'], $contacts)];
    }, fn($v) => [$v[0], 'contacts: ' . implode(', ', $v[1])]);

scenario('REQ-CRS-07', 'unauthorized', 'student1 has enrol/manual:enrol in course?', function() use ($ctx, $student1) {
    return has_capability('enrol/manual:enrol', $ctx, $student1);
}, fn($v) => [$v === false, $v ? 'yes' : 'no']);

scenario('REQ-CRS-07', 'normal', 'teacher1 (editingteacher) has enrol/manual:enrol in course?', function() use ($ctx, $teacher1) {
    return has_capability('enrol/manual:enrol', $ctx, $teacher1);
}, fn($v) => [$v === true, $v ? 'yes' : 'no']);

scenario('REQ-CRS-07', 'unauthorized', 'roles teacher1 may assign in course (editingteacher not included?)', function() use ($ctx, $teacher1) {
    \core\session\manager::set_user($teacher1);
    $roles = get_assignable_roles($ctx, ROLENAME_SHORT, false, $teacher1);
    \core\session\manager::set_user(get_admin());
    return array_values($roles);
}, fn($v) => [!in_array('editingteacher', $v) && in_array('student', $v), 'assignable: ' . implode(', ', $v)]);

// ---------------------------------------------------------------- Self enrolment (REQ-CRS-08)
$self = enrol_get_plugin('self');
$sid = $self->add_instance($course, [
    'status' => ENROL_INSTANCE_ENABLED, 'customint6' => 1, 'customint3' => 2, 'enrolperiod' => 30 * DAYSECS,
    'roleid' => $studentrole, 'password' => '',
]);
$sinst = $DB->get_record('enrol', ['id' => $sid]);
$asuser = function($user, callable $fn) {
    \core\session\manager::set_user($user);
    try {
        return $fn();
    } finally {
        \core\session\manager::set_user(get_admin());
    }
};
$selfcheck = fn($inst, $user) => $asuser($user, fn() => $self->can_self_enrol($inst));
$u1 = $mkuser('ver_self1');
$u2 = $mkuser('ver_self2');
$u3 = $mkuser('ver_self3');

scenario('REQ-CRS-08', 'normal', 'self enrol allowed; enrolment period sets timeend', function() use ($self, $sinst, $u1, $asuser, $selfcheck, $DB) {
    $ok = $selfcheck($sinst, $u1);
    $asuser($u1, fn() => $self->enrol_self($sinst, (object) []));
    $ue = $DB->get_record('user_enrolments', ['enrolid' => $sinst->id, 'userid' => $u1->id]);
    return [$ok, $ue->timeend - $ue->timestart];
}, fn($v) => [$v[0] === true && $v[1] == 30 * DAYSECS, 'can=' . var_export($v[0], true) . ', timeend-timestart=' . $v[1]]);

scenario('REQ-CRS-08', 'invalid', 'self enrol again when already enrolled', function() use ($sinst, $u1, $selfcheck) {
    return $selfcheck($sinst, $u1);
}, fn($v) => [$v === get_string('canntenrol', 'enrol_self'), 'message: ' . strip_tags((string) $v)]);

scenario('REQ-CRS-08', 'boundary', 'max enrolled = 2: second user still allowed', function() use ($self, $sinst, $u2, $asuser, $selfcheck) {
    $ok = $selfcheck($sinst, $u2);
    $asuser($u2, fn() => $self->enrol_self($sinst, (object) []));
    return $ok;
}, fn($v) => [$v === true, 'can=' . var_export($v, true)]);

scenario('REQ-CRS-08', 'boundary', 'max enrolled = 2: third user rejected', function() use ($sinst, $u3, $selfcheck) {
    return $selfcheck($sinst, $u3);
}, fn($v) => [$v === get_string('maxenrolledreached', 'enrol_self'), 'message: ' . strip_tags((string) $v)]);

scenario('REQ-CRS-08', 'invalid', 'enrolment opens tomorrow (enrolstartdate in future)', function() use ($sinst, $u3, $selfcheck, $now) {
    $i = clone $sinst; $i->customint3 = 0; $i->enrolstartdate = $now + DAYSECS;
    return $selfcheck($i, $u3);
}, fn($v) => [is_string($v) && str_starts_with(strip_tags($v), 'You cannot enrol yet'),
    'message: ' . strip_tags((string) $v)]);

scenario('REQ-CRS-08', 'invalid', 'enrolment closed yesterday (enrolenddate in past)', function() use ($sinst, $u3, $selfcheck, $now) {
    $i = clone $sinst; $i->customint3 = 0; $i->enrolenddate = $now - DAYSECS;
    return $selfcheck($i, $u3);
}, fn($v) => [is_string($v) && str_starts_with(strip_tags($v), 'You cannot enrol any more'), 'message: ' . strip_tags((string) $v)]);

scenario('REQ-CRS-08', 'invalid', '"Allow new self enrolments" = No', function() use ($sinst, $u3, $selfcheck) {
    $i = clone $sinst; $i->customint3 = 0; $i->customint6 = 0;
    return $selfcheck($i, $u3);
}, fn($v) => [$v === get_string('canntenrol', 'enrol_self'), 'message: ' . strip_tags((string) $v)]);

scenario('REQ-CRS-08', 'unauthorized', 'guest user cannot self enrol', function() use ($sinst, $selfcheck) {
    $i = clone $sinst; $i->customint3 = 0;
    return $selfcheck($i, guest_user());
}, fn($v) => [is_string($v) && str_contains(strip_tags($v), get_string('noguestaccess', 'enrol')), 'message: ' . strip_tags((string) $v)]);

scenario('REQ-CRS-08', 'invalid', 'wrong enrolment key is rejected by the form', function() use ($self, $sinst, $u3, $asuser) {
    $i = clone $sinst; $i->customint3 = 0; $i->password = 'secret-key';
    return $asuser($u3, function() use ($i) {
        // Call the real form validation without rendering the form.
        $rc = new ReflectionClass(\enrol_self\form\enrol_form::class);
        $form = $rc->newInstanceWithoutConstructor();
        $prop = $rc->getProperty('instance');
        $prop->setValue($form, $i);
        return $form->validation(['enrolpassword' => 'wrong'], []);
    });
}, fn($e) => [isset($e['enrolpassword']), 'error: ' . strip_tags($e['enrolpassword'] ?? 'none')]);

// ---------------------------------------------------------------- Roll back and report
try {
    $transaction->rollback(new \Exception('verify_rules: rollback'));
} catch (\Exception $e) {
    // Expected: rollback rethrows the exception we gave it.
}
purge_caches(['muc' => true]);

printf("Moodle %s, %s\n", $CFG->release, date('Y-m-d H:i'));
echo implode("\n", $results), "\n";
printf("\n%d scenarios, %d failed. All changes rolled back.\n", count($results), $failures);
exit($failures ? 1 : 0);
