#!/usr/bin/env bash
# Load the Fig LMS sample data into the running Moodle container.
# Usage (from the repository root): ./infra/sample-data/load.sh
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose -f docker-compose.yml exec -T -u www-data moodle php /opt/fig/sample-data/load.php
