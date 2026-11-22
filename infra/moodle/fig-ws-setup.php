<?php
// Fig LMS: enable web services + REST and create the token used by the golden master tests (#12).
// Idempotent: safe to run on every container start. Prints the token on the last line.
//
// Run manually:  docker compose -f infra/docker-compose.yml exec -u www-data moodle php /opt/fig/fig-ws-setup.php

define('CLI_SCRIPT', true);
require('/var/www/moodle/config.php');
require_once($CFG->libdir . '/clilib.php');
require_once($CFG->dirroot . '/webservice/lib.php');

const FIG_SERVICE_SHORTNAME = 'fig_golden_master';

// Read-only functions the golden master harness needs. Extend this list when a test needs more.
const FIG_FUNCTIONS = [
    'core_webservice_get_site_info',
    'core_course_get_courses',
    'core_course_get_courses_by_field',
    'core_course_get_categories',
    'core_course_get_contents',
    'core_course_get_course_module',
    'core_course_search_courses',
    'core_enrol_get_enrolled_users',
    'core_enrol_get_users_courses',
    'core_enrol_get_course_enrolment_methods',
    'core_user_get_users_by_field',
    'core_completion_get_course_completion_status',
    'core_completion_get_activities_completion_status',
    'mod_assign_get_assignments',
    'mod_assign_get_submissions',
    'mod_assign_get_submission_status',
    'mod_assign_get_grades',
    'gradereport_user_get_grade_items',
    'gradereport_user_get_grades_table',
];

// 0. The CLI installer leaves the no-reply address empty, which causes PHP warnings when users are created.
if (empty($CFG->noreplyaddress)) {
    set_config('noreplyaddress', 'noreply@example.com');
}

// 1. Web services on, REST protocol on.
set_config('enablewebservices', 1);
$protocols = array_filter(explode(',', get_config('core', 'webserviceprotocols') ?: ''));
if (!in_array('rest', $protocols)) {
    $protocols[] = 'rest';
    set_config('webserviceprotocols', implode(',', $protocols));
}

// 2. External service with the functions above.
$wsman = new webservice();
$service = $wsman->get_external_service_by_shortname(FIG_SERVICE_SHORTNAME);
if (!$service) {
    $serviceid = $wsman->add_external_service((object) [
        'name' => 'Fig golden master',
        'shortname' => FIG_SERVICE_SHORTNAME,
        'enabled' => 1,
        'restrictedusers' => 0,
        'downloadfiles' => 0,
        'uploadfiles' => 0,
        'requiredcapability' => '',
    ]);
    $service = $wsman->get_external_service_by_id($serviceid);
}
foreach (FIG_FUNCTIONS as $function) {
    if ($DB->record_exists('external_functions', ['name' => $function])
            && !$wsman->service_function_exists($function, $service->id)) {
        $wsman->add_external_function_to_service($function, $service->id);
    }
}

// 3. Permanent token for the admin user. If MOODLE_WS_TOKEN is set in .env, that exact value is used,
//    so every teammate has the same token in their .env.
$admin = get_admin();
\core\session\manager::set_user($admin);

$wanted = trim((string) getenv('MOODLE_WS_TOKEN'));
if ($wanted !== '' && !preg_match('/^[a-f0-9]{32}$/', $wanted)) {
    cli_error('MOODLE_WS_TOKEN must be 32 lowercase hex characters (e.g. output of: openssl rand -hex 16).');
}

$token = $DB->get_record('external_tokens', [
    'userid' => $admin->id,
    'externalserviceid' => $service->id,
    'tokentype' => EXTERNAL_TOKEN_PERMANENT,
]);
if (!$token) {
    \core_external\util::generate_token(
        EXTERNAL_TOKEN_PERMANENT, $service, $admin->id, context_system::instance(), 0, '', 'golden-master'
    );
    $token = $DB->get_record('external_tokens', [
        'userid' => $admin->id,
        'externalserviceid' => $service->id,
        'tokentype' => EXTERNAL_TOKEN_PERMANENT,
    ], '*', MUST_EXIST);
}
if ($wanted !== '' && $token->token !== $wanted) {
    $DB->set_field('external_tokens', 'token', $wanted, ['id' => $token->id]);
    $token->token = $wanted;
}

echo "Web services and REST enabled. Service: " . FIG_SERVICE_SHORTNAME . "\n";
echo "MOODLE_WS_TOKEN=" . $token->token . "\n";
