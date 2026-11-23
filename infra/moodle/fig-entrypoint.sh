#!/usr/bin/env bash
# Fig LMS Moodle entrypoint.
#  - "apache2-foreground" (web container): wait for the DB, install Moodle on first start,
#    enable web services and create the REST token, then start Apache.
#  - "cron" (cron container): wait until Moodle is installed, then run cron every minute.
set -Eeuo pipefail

cd /var/www/moodle
chown -R www-data:www-data /var/www/moodledata

as_www() { su -m www-data -s /bin/bash -c "$1"; }

db_query() {
  php -r '
    $c = @pg_connect(sprintf("host=%s dbname=%s user=%s password=%s",
        getenv("MOODLE_DB_HOST"), getenv("MOODLE_DB_NAME"), getenv("MOODLE_DB_USER"), getenv("MOODLE_DB_PASSWORD")));
    if (!$c) { exit(2); }
    $r = @pg_query($c, $argv[1]);
    exit(($r && pg_num_rows($r) > 0) ? 0 : 1);
  ' "$1"
}

echo "[fig] Waiting for PostgreSQL at ${MOODLE_DB_HOST}..."
until db_query "SELECT 1"; do sleep 2; done

is_installed() { db_query "SELECT 1 FROM mdl_config WHERE name = 'version'"; }

if [ "${1:-}" = "cron" ]; then
  echo "[fig] Waiting for Moodle to be installed..."
  until is_installed; do sleep 5; done
  echo "[fig] Running Moodle cron every 60 seconds."
  while true; do
    as_www "php admin/cli/cron.php" || echo "[fig] cron run failed"
    sleep 60
  done
fi

if ! is_installed; then
  echo "[fig] First start: installing Moodle 5.3.1 (takes a few minutes)..."
  as_www 'php admin/cli/install_database.php --agree-license --lang=en \
    --adminuser=admin --adminpass="$MOODLE_ADMIN_PASSWORD" --adminemail=admin@example.com \
    --fullname="Fig LMS - Moodle 5.3.1 baseline" --shortname="figmoodle"'
  echo "[fig] Moodle installed."
fi

as_www "php /opt/fig/fig-ws-setup.php"

exec moodle-docker-php-entrypoint "$@"
