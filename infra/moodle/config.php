<?php
// Moodle configuration for the Fig LMS Docker setup.
// All values come from environment variables set in infra/docker-compose.yml (and infra/.env).

unset($CFG);
global $CFG;
$CFG = new stdClass();

$CFG->dbtype    = 'pgsql';
$CFG->dblibrary = 'native';
$CFG->dbhost    = getenv('MOODLE_DB_HOST') ?: 'moodle-db';
$CFG->dbname    = getenv('MOODLE_DB_NAME') ?: 'moodle';
$CFG->dbuser    = getenv('MOODLE_DB_USER') ?: 'moodle';
$CFG->dbpass    = getenv('MOODLE_DB_PASSWORD') ?: '';
$CFG->prefix    = 'mdl_';
$CFG->dboptions = [
    'dbpersist' => 0,
    'dbport'    => 5432,
];

$CFG->wwwroot   = getenv('MOODLE_WWWROOT') ?: 'http://localhost:8000';
$CFG->dataroot  = '/var/www/moodledata';
$CFG->admin     = 'admin';
$CFG->directorypermissions = 02777;
$CFG->routerconfigured = false;

// Local development only: never send mail. Debugging stays at Moodle's default (off) so measurements
// match a normal installation; turn it on in Site administration > Development > Debugging if needed.
$CFG->noemailever = true;

require_once(__DIR__ . '/lib/setup.php');
