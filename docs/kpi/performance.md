# Performance KPIs: Moodle baseline

Baseline response times of Moodle 5.3.1 (`v5.3.1`) for the pages and calls our modules replace. At the end of the project, the new system is measured with the same script, on the same machine and with the same data, and compared against these numbers.

## Results

Each KPI was measured in 3 runs. Baseline runs (PERF-01…05): one user, 5 warm-up requests, then 30 measured requests per page. Load runs (PERF-06): 20 users for 60 s. The baseline value is the median over the 3 runs. Raw results are in [`performance/results/`](performance/results/), and the table is produced by [`performance/summarize.py`](performance/summarize.py).

| KPI | What | Runs | Median per run (ms) | p95 per run (ms) | **Median** (ms) | **p95** (ms) |
|---|---|---:|---|---|---:|---:|
| PERF-01 | Login (POST + redirect to dashboard) | 3 | 90.1 / 90.3 / 87.7 | 121.6 / 115.8 / 112.5 | **90.1** | **115.8** |
| PERF-02 | Course page (CS101, student) | 3 | 36.7 / 37.6 / 38.4 | 40.4 / 39.5 / 44.0 | **37.6** | **40.4** |
| PERF-03 | Assignment page (submission status, student) | 3 | 51.7 / 49.7 / 49.9 | 71.2 / 54.5 / 59.2 | **49.9** | **59.2** |
| PERF-04 | Gradebook (grader report, teacher) | 3 | 46.3 / 46.9 / 48.0 | 51.1 / 49.9 / 54.8 | **46.9** | **51.1** |
| PERF-05 | REST `core_course_get_courses` | 3 | 11.8 / 12.0 / 13.2 | 15.3 / 16.9 / 15.6 | **12.0** | **15.6** |
| PERF-06 | Course page, 20 concurrent users | 3 | 73.8 / 83.1 / 80.3 | 148.6 / 166.0 / 158.5 | **80.3** | **158.5** |

- **Errors:** 0% in every run. Every response was HTTP 200, was not redirected to the login page, and contained the expected content.
- **Load (PERF-06):** about 1,080 course page requests per run (17.8–18.0 per second). Under 20 concurrent users, the median is about 2× and the p95 about 4× the single-user value (37.6 → 80.3 ms median, 40.4 → 158.5 ms p95).

## KPIs

| KPI | Category | Definition | Unit | Moodle baseline | Proposed target (new system) | How measured |
|---|---|---|---|---|---|---|
| PERF-01 Login time | performance | Time from sending the login form until the page it redirects to has been received (POST `/login/index.php` + redirects to `/my/`) | ms (median / p95) | 90.1 / 115.8 | p95 ≤ 115 ms | k6, `perf_login` |
| PERF-02 Course page | performance | Server response time of the course page for an enrolled student | ms (median / p95) | 37.6 / 40.4 | p95 ≤ 40 ms | k6, `perf_course_page` |
| PERF-03 Assignment page | performance | Server response time of the assignment page (submission status) for a student | ms (median / p95) | 49.9 / 59.2 | p95 ≤ 60 ms | k6, `perf_assign_page` |
| PERF-04 Gradebook | performance | Server response time of the grader report for the course teacher | ms (median / p95) | 46.9 / 51.1 | p95 ≤ 50 ms | k6, `perf_gradebook` |
| PERF-05 REST call | performance | Response time of `core_course_get_courses` (all courses) with a token; the new system is measured on its equivalent endpoint | ms (median / p95) | 12.0 / 15.6 | p95 ≤ 15 ms | k6, `perf_rest_get_courses` |
| PERF-06 Course page under load | performance | Course page response time with 20 logged-in students, each opening the page once per second for 60 s | ms (median / p95), error % | 80.3 / 158.5, 0% | p95 ≤ 160 ms, 0% errors | k6, `perf_load_course_page` |

Targets are proposals for team discussion. The rule behind them: **the new system must not be slower than Moodle** (p95 ≤ Moodle's p95, rounded) on the same machine and with the same data. Moodle is already fast on this small dataset, so the more interesting result is how both behave under load (PERF-06), and a larger load test is a candidate for the final measurement.

## How it was measured (repeatable)

**Setup**
1. Start Moodle with the sample data (#8):
   ```bash
   cp infra/.env.example infra/.env
   docker compose -f infra/docker-compose.yml up -d
   ./infra/sample-data/load.sh
   ```
2. Install k6: `brew install k6` (macOS), or see https://grafana.com/docs/k6/latest/set-up/install-k6/. Used: k6 v2.4.0.

**Run** (from the repository root; the script reads the passwords and token from `infra/.env`)
```bash
set -a; . infra/.env; set +a
for r in 1 2 3; do
  k6 run -q -e MODE=baseline -e RUN=$r docs/kpi/performance/moodle-perf.js; sleep 10
  k6 run -q -e MODE=load     -e RUN=$r docs/kpi/performance/moodle-perf.js; sleep 10
done
python3 docs/kpi/performance/summarize.py
```
One full round takes about 4 minutes. Results go to `docs/kpi/performance/results/<mode>-run<N>.json`.

**What the script does** ([`performance/moodle-perf.js`](performance/moodle-perf.js))
| KPI | Request | User | Check |
|---|---|---|---|
| PERF-01 | GET login form (token), POST `/login/index.php`, follow redirects | student1, new session every time | ends on a non-login page |
| PERF-02 | GET `/course/view.php?id=2` | student1 | HTTP 200, contains "Assignment 1" |
| PERF-03 | GET `/mod/assign/view.php?id=1` | student1 | HTTP 200, contains "Submission status" |
| PERF-04 | GET `/grade/report/grader/index.php?id=2` | teacher1 | HTTP 200, contains a student name |
| PERF-05 | POST `/webservice/rest/server.php`, `core_course_get_courses` | token of `fig_golden_master` | HTTP 200, JSON array with ≥ 4 courses |
| PERF-06 | GET `/course/view.php?id=2`, then 1 s think time, for 60 s | 20 users (student1–5, 4 sessions each), each logged in once | as PERF-02 |

- **Response time** is k6's `http_req_duration` for the request (sending + waiting + receiving), measured from the host against `localhost:8000`. It covers the **server-generated HTML only**: no CSS, JS, images or browser rendering. Browser-side times belong to the usability KPIs (#16).
- **Median and p95** are computed per run over the 30 measured samples (baseline) or over all requests (load). The baseline value is the median over the 3 runs.
- Warm-up requests are not counted, so all values are for a warm cache.

## Machine and software
Numbers depend on hardware, so they are only comparable when measured on the same machine.

| | |
|---|---|
| Machine | Apple MacBook, Apple M2, 8 CPU cores, 8 GB RAM, macOS 26.6.2 |
| Docker | Docker Desktop, Docker 29.1.3, VM limited to 8 CPUs and 3.8 GB RAM |
| Moodle | 5.3.1 (Build 20261112), PHP 8.3.37 (OPcache on, `memory_limit` 128M), Apache 2.4.71 (prefork), PostgreSQL 17.12, debugging off |
| Data | Sample data from #8: 3 courses, 7 users, 1 assignment, 3 grades |
| Load tool | k6 v2.4.0, running on the same machine |
| Date | 2026-11-22 |

## Limitations
- **Very small dataset.** With 3 courses and 7 users, Moodle is fast. Times for the gradebook and the course page grow with the number of students and activities, so a larger dataset should be measured for the final comparison, the same for both systems (`admin/tool/generator` can create one).
- **Client and server share one laptop.** k6 and Docker compete for CPU, and the `moodle-cron` container runs every minute (up to about 60% of one core while it runs). This is why p95 varies between runs (e.g. PERF-03 54–71 ms). The median is stable within about ±5%.
- **Throughput is not a capacity measure.** In PERF-06, the throughput (about 18 requests/s) is set by the 1 s think time, not by Moodle's limits. A stress test without think time is needed to find the maximum.
- **Server time only.** Page weight and browser rendering are not included (see #16).
