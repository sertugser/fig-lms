#!/usr/bin/env python3
"""Maintainability baseline for the Moodle code of the 6 Fig LMS modules.

Usage:
    python measure.py <path-to-moodle-public-folder> [output-dir]

Needs: Python 3.9+, `pip install lizard pygount`
Writes into output-dir (default: ./results):
    modules.csv      one row per module (lines, tests, complexity summary)
    top5.csv         the 5 most complex functions per module
    functions.csv    every function (module, file, name, ccn, nloc)
"""
import csv
import os
import statistics
import sys

import lizard
from pygount import SourceAnalysis

# Module -> Moodle paths, relative to public/. Folders are walked recursively.
# Source of truth for the mapping; change it here and re-run.
MODULES = {
    "identity":   ["login", "auth/manual", "admin/roles", "user", "lib/accesslib.php"],
    "course":     ["course", "enrol/manual", "enrol/self", "enrol/category"],
    "advising":   ["availability", "completion"],
    "assignment": ["mod/assign"],
    "grading":    ["grade", "lib/gradelib.php"],
    # New module, no Moodle equivalent. Closest reference: learning plans.
    "planning":   ["competency"],
}
SKIP_DIRS = {"lang", "vendor", "node_modules", "amd", "yui"}
CCN_LIMIT = 10  # a function above this counts as "complex"


def php_files(root, paths):
    for p in paths:
        full = os.path.join(root, p)
        if os.path.isfile(full):
            yield p.replace("\\", "/")
            continue
        for dirpath, dirs, files in os.walk(full):
            dirs[:] = sorted(d for d in dirs if d not in SKIP_DIRS)
            for f in sorted(files):
                if f.endswith(".php"):
                    yield os.path.relpath(os.path.join(dirpath, f), root).replace("\\", "/")


def is_test(rel):
    return "/tests/" in rel


def code_lines(full):
    a = SourceAnalysis.from_file(full, "moodle", encoding="utf-8")
    return a.code_count


def main():
    root = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else "results"
    os.makedirs(out, exist_ok=True)
    mod_rows, top_rows, fn_rows = [], [], []
    for mod, paths in MODULES.items():
        src_lines = test_lines = src_files = test_files = feat_lines = 0
        funcs = []
        for rel in php_files(root, paths):
            full = os.path.join(root, rel)
            n = code_lines(full)
            if is_test(rel):
                test_lines += n
                test_files += 1
                continue
            src_lines += n
            src_files += 1
            info = lizard.analyze_file(full)
            for f in info.function_list:
                funcs.append((rel, (f.long_name.split() or ["<anonymous>"])[0], f.cyclomatic_complexity, f.nloc, f.start_line))
        # Behat feature files (acceptance tests) are counted separately.
        for p in paths:
            base = os.path.join(root, p)
            if os.path.isdir(base):
                for dp, _, fs in os.walk(base):
                    if "/tests/" in dp.replace("\\", "/") + "/":
                        for f in fs:
                            if f.endswith(".feature"):
                                with open(os.path.join(dp, f), encoding="utf-8") as fh:
                                    feat_lines += sum(1 for l in fh if l.strip() and not l.strip().startswith("#"))
        ccn = [f[2] for f in funcs]
        mod_rows.append({
            "module": mod, "paths": " ".join(paths),
            "src_files": src_files, "src_code_lines": src_lines,
            "test_files": test_files, "test_code_lines": test_lines,
            "test_to_code_ratio": round(test_lines / src_lines, 3) if src_lines else 0,
            "behat_feature_lines": feat_lines,
            "functions": len(funcs),
            "ccn_mean": round(statistics.mean(ccn), 2),
            "ccn_median": statistics.median(ccn),
            "ccn_max": max(ccn),
            "functions_over_limit": sum(c > CCN_LIMIT for c in ccn),
            "pct_over_limit": round(100 * sum(c > CCN_LIMIT for c in ccn) / len(ccn), 1),
        })
        for rank, f in enumerate(sorted(funcs, key=lambda x: -x[2])[:5], 1):
            top_rows.append({"module": mod, "rank": rank, "function": f[1], "file": f[0],
                             "line": f[4], "ccn": f[2], "nloc": f[3]})
        fn_rows += [{"module": mod, "file": f[0], "function": f[1], "ccn": f[2], "nloc": f[3]} for f in funcs]
        print(f"{mod}: {src_lines} src / {test_lines} test lines, {len(funcs)} functions, mean CCN {mod_rows[-1]['ccn_mean']}")

    for name, rows in (("modules.csv", mod_rows), ("top5.csv", top_rows), ("functions.csv", fn_rows)):
        with open(os.path.join(out, name), "w", newline="", encoding="utf-8") as fh:
            w = csv.DictWriter(fh, fieldnames=list(rows[0]))
            w.writeheader()
            w.writerows(rows)


if __name__ == "__main__":
    main()
