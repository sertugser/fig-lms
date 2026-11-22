<?php
define('CLI_SCRIPT', true);
require '/var/www/moodle/config.php';

$course = $DB->get_record('course', ['shortname' => 'CS101'], '*', MUST_EXIST);
$coursecontext = context_course::instance($course->id);
$assignment = $DB->get_record('assign', ['course' => $course->id], '*', MUST_EXIST);
$cm = get_coursemodule_from_instance('assign', $assignment->id, $course->id, false, MUST_EXIST);
$modulecontext = context_module::instance($cm->id);

$results = [
    'moodle' => [
        'version' => (int)$CFG->version,
        'course_id' => $course->id,
        'assignment_module_id' => $cm->id,
    ],
    'users' => [],
];

foreach (['student1', 'teacher1', 'admin'] as $username) {
    $user = $DB->get_record('user', ['username' => $username], '*', MUST_EXIST);
    $roles = get_user_roles($coursecontext, $user->id);
    $results['users'][$username] = [
        'roles' => array_values(array_map(static fn($role) => $role->shortname, $roles)),
        'site_admin' => is_siteadmin($user->id),
        'view_assignment' => has_capability('mod/assign:view', $modulecontext, $user->id),
        'create_assignment' => has_capability('moodle/course:manageactivities', $coursecontext, $user->id),
        'grade_assignment' => has_capability('mod/assign:grade', $modulecontext, $user->id),
        'enrol_users' => has_capability('enrol/manual:enrol', $coursecontext, $user->id),
        'assign_roles' => has_capability('moodle/role:assign', $coursecontext, $user->id),
    ];
}

$results['settings'] = [
    'sessiontimeout_seconds' => (int)$CFG->sessiontimeout,
    'lockoutthreshold' => (int)$CFG->lockoutthreshold,
    'lockoutwindow_seconds' => (int)$CFG->lockoutwindow,
    'lockoutduration_seconds' => (int)$CFG->lockoutduration,
    'passwordpolicy' => (int)$CFG->passwordpolicy,
    'minpasswordlength' => (int)$CFG->minpasswordlength,
    'minpassworddigits' => (int)$CFG->minpassworddigits,
    'minpasswordlower' => (int)$CFG->minpasswordlower,
    'minpasswordupper' => (int)$CFG->minpasswordupper,
    'minpasswordnonalphanum' => (int)$CFG->minpasswordnonalphanum,
    'passwordpolicycheckonlogin' => (int)$CFG->passwordpolicycheckonlogin,
];

echo json_encode($results, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES) . PHP_EOL;
