#!/usr/bin/env bash
# Golden master #13 in one command: prerequisite check, Moodle vs new system.
#
#   ./run-prerequisite-golden-master.sh          (in tests/golden-master)
#   ./run-prerequisite-golden-master.sh --all    also runs the other golden master tests (CI does this)
#
# 1. Starts a throwaway PostgreSQL 17 in Docker (container fig-golden-master-db, port 55432), unless POSTGRES_HOST is set.
# 2. Builds the backend and starts it on port 18080 with the sample data (Flyway).
# 3. Runs PrerequisiteGoldenMasterTest: Moodle live if it runs and MOODLE_WS_TOKEN is set, otherwise the saved snapshots.
# 4. Stops the backend and the database again, and prints the comparison table.
#
# Needs Java 21 and, without POSTGRES_HOST, Docker. Windows: run it in Git Bash or WSL.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/../.." && pwd)"

port="${FIG_BACKEND_PORT:-18080}"
export SAMPLE_USER_PASSWORD="${SAMPLE_USER_PASSWORD:-golden-master-pass}"
export FIG_BACKEND_URL="http://localhost:$port"

db_container=""
backend_pid=""
cleanup() {
    if [[ -n "$backend_pid" ]]; then kill "$backend_pid" 2>/dev/null || true; wait "$backend_pid" 2>/dev/null || true; fi
    if [[ -n "$db_container" ]]; then docker rm -f "$db_container" >/dev/null 2>&1 || true; fi
}
trap cleanup EXIT

if [[ -z "${POSTGRES_HOST:-}" ]]; then
    db_container="fig-golden-master-db"
    export POSTGRES_HOST=localhost POSTGRES_PORT=55432 POSTGRES_DB=fig POSTGRES_USER=fig POSTGRES_PASSWORD=golden-master-db
    echo "Starting PostgreSQL 17 ($db_container, port $POSTGRES_PORT)..."
    docker rm -f "$db_container" >/dev/null 2>&1 || true
    docker run -d --name "$db_container" -p "$POSTGRES_PORT:5432" \
        -e POSTGRES_DB="$POSTGRES_DB" -e POSTGRES_USER="$POSTGRES_USER" -e POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
        postgres:17 >/dev/null
    for _ in $(seq 1 60); do
        docker exec "$db_container" pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null 2>&1 && break
        sleep 1
    done
fi

echo "Building the backend..."
(cd "$root/backend" && ./mvnw -B -q package -DskipTests)
jar="$(ls "$root"/backend/target/fig-lms-backend-*.jar | grep -v plain | head -n 1)"

mkdir -p "$here/target"
echo "Starting the backend on port $port..."
SERVER_PORT="$port" java -jar "$jar" >"$here/target/backend.log" 2>&1 &
backend_pid=$!
for _ in $(seq 1 90); do
    if curl -fsS -o /dev/null "$FIG_BACKEND_URL/actuator/health" 2>/dev/null; then break; fi
    if ! kill -0 "$backend_pid" 2>/dev/null; then
        echo "The backend stopped. Log: $here/target/backend.log" >&2
        tail -n 40 "$here/target/backend.log" >&2
        exit 1
    fi
    sleep 1
done

cd "$here"
if [[ "${1:-}" == "--all" ]]; then
    ./mvnw -B test -Dgolden.requireBackend=true
else
    ./mvnw -B test -Dtest=PrerequisiteGoldenMasterTest -Dgolden.requireBackend=true
fi
