#!/usr/bin/env python3
"""Turn the clover reports of run_coverage.py into the MAINT-05 numbers.

Usage:
    python docs/kpi/maintainability/phpunit-coverage/summarize_coverage.py <path-to-moodle-public-folder>

Needs `pip install lizard` (measure.py is imported, so the file set is exactly the one of the other KPIs).
Reads  docs/kpi/maintainability/results/phpunit/<module>-clover.xml and <module>-junit.xml,
writes docs/kpi/maintainability/results/phpunit/coverage.csv and prints one row per module.

Line coverage = covered executable lines / executable lines (PHPUnit "statements"), over the production PHP files of the
module (no tests, no language packs, no third-party code). Files that are in the module but missing from the report count as
not measured and are reported separately.
"""
import csv
import importlib.util
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

HERE = Path(__file__).resolve().parent
RESULTS = HERE.parents[0] / "results" / "phpunit"
PREFIX = "/var/www/moodle/public/"

spec = importlib.util.spec_from_file_location("measure", HERE.parent / "measure.py")
measure = importlib.util.module_from_spec(spec)
spec.loader.exec_module(measure)


def clover_files(path):
    out = {}
    for f in ET.parse(path).getroot().iter("file"):
        m = f.find("metrics")
        name = f.get("name", "")
        if m is not None and name.startswith(PREFIX):
            out[name[len(PREFIX):]] = (int(m.get("statements")), int(m.get("coveredstatements")))
    return out


def junit_totals(path):
    if not path.exists():
        return {}
    root = ET.parse(path).getroot()
    suite = root if root.tag == "testsuite" else root.find("testsuite")
    return {k: suite.get(k) for k in ("tests", "failures", "errors", "skipped", "time")} if suite is not None else {}


def minutes(path):
    if not path.exists():
        return ""
    m = re.search(r"minutes: ([\d.]+)", path.read_text(encoding="utf-8"))
    return m.group(1) if m else ""


def main():
    root = sys.argv[1]
    tp = measure.third_party(root)
    rows = []
    for mod, paths in measure.MODULES.items():
        clover = RESULTS / f"{mod}-clover.xml"
        if not clover.exists():
            rows.append({"module": mod, "status": "not measured"})
            continue
        report = clover_files(clover)
        scope = [r for r in dict.fromkeys(measure.module_files(root, paths, tp))
                 if r.endswith(".php") and not measure.is_test(r)]
        stmts = covered = missing = 0
        for r in scope:
            if r in report:
                stmts += report[r][0]
                covered += report[r][1]
            else:
                missing += 1
        junit = junit_totals(RESULTS / f"{mod}-junit.xml")
        rows.append({
            "module": mod, "status": "measured", "php_files": len(scope), "files_not_in_report": missing,
            "executable_lines": stmts, "covered_lines": covered,
            "line_coverage_pct": round(100 * covered / stmts, 1) if stmts else "",
            "tests": junit.get("tests", ""), "failures": junit.get("failures", ""), "errors": junit.get("errors", ""),
            "skipped": junit.get("skipped", ""), "minutes": minutes(RESULTS / f"{mod}-run.txt"),
        })
    fields = ["module", "status", "php_files", "files_not_in_report", "executable_lines", "covered_lines",
              "line_coverage_pct", "tests", "failures", "errors", "skipped", "minutes"]
    with open(RESULTS / "coverage.csv", "w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=fields)
        w.writeheader()
        w.writerows(rows)
    for r in rows:
        print({k: r.get(k, "") for k in fields})


if __name__ == "__main__":
    main()
