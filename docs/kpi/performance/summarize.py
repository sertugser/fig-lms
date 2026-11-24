"""Summarise the k6 result files of the Moodle performance baseline (#15).

Usage: python3 docs/kpi/performance/summarize.py [results_dir]
Reads <mode>-run<N>.json written by moodle-perf.js and prints a Markdown table:
per KPI the median and p95 of each run, and the median over the runs (the baseline value).
"""
import json
import statistics
import sys
from pathlib import Path

KPIS = [
    ("PERF-01", "baseline", "perf_login", "Login (POST + redirect to dashboard)"),
    ("PERF-02", "baseline", "perf_course_page", "Course page (CS101, student)"),
    ("PERF-03", "baseline", "perf_assign_page", "Assignment page (submission status, student)"),
    ("PERF-04", "baseline", "perf_gradebook", "Gradebook (grader report, teacher)"),
    ("PERF-05", "baseline", "perf_rest_get_courses", "REST core_course_get_courses"),
    ("PERF-06", "load", "perf_load_course_page", "Course page, 20 concurrent users"),
]


def main() -> None:
    results = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).parent / "results")
    runs = {"baseline": [], "load": []}
    for f in sorted(results.glob("*-run*.json")):
        data = json.loads(f.read_text())
        runs[data["mode"]].append(data)

    print("| KPI | What | Runs | Median per run (ms) | p95 per run (ms) | **Median** (ms) | **p95** (ms) |")
    print("|---|---|---:|---|---|---:|---:|")
    for kpi, mode, metric, label in KPIS:
        values = [r["metrics"][metric] for r in runs[mode] if r["metrics"].get(metric)]
        if not values:
            continue
        meds = [v["med"] for v in values]
        p95s = [v["p(95)"] for v in values]
        print(f"| {kpi} | {label} | {len(values)} | {' / '.join(f'{x:.1f}' for x in meds)} | "
              f"{' / '.join(f'{x:.1f}' for x in p95s)} | **{statistics.median(meds):.1f}** | "
              f"**{statistics.median(p95s):.1f}** |")

    print()
    for r in runs["load"]:
        m = r["metrics"]
        print(f"- load run {r['run']}: {m['perf_load_requests']['count']} course page requests "
              f"({m['perf_load_requests']['rate']:.1f}/s), errors {m['perf_errors']['rate'] * 100:.2f}%, "
              f"failed HTTP requests {m['http_req_failed']['rate'] * 100:.2f}%")
    for r in runs["baseline"]:
        print(f"- baseline run {r['run']}: errors {r['metrics']['perf_errors']['rate'] * 100:.2f}%")


if __name__ == "__main__":
    main()
