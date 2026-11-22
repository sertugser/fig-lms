#!/usr/bin/env python3
"""Maintainability baseline for the Moodle code of the 6 Fig LMS modules.

Usage:
    python measure.py <path-to-moodle-public-folder> [output-dir]

Needs: Python 3.9+, `pip install lizard`, and `cloc` on the PATH
(https://github.com/AlDanial/cloc, e.g. `apt install cloc` or `brew install cloc`).
Writes into output-dir (default: ./results):
    modules.csv      one row per module (lines per language, tests, complexity summary)
    top5.csv         the 5 most complex functions per module
    functions.csv    every function (module, file, name, ccn, nloc)
"""
import csv
import json
import os
import re
import statistics
import subprocess
import sys
import tempfile

import lizard

# Module -> Moodle paths, relative to public/. Folders are walked recursively.
# Source of truth for the mapping; change it here and re-run.
# Same folder list as the team's cloc line-count table, plus `user/` for identity.
MODULES = {
    "identity": [
        "auth", "login", "admin/roles", "user",
        "lib/accesslib.php", "lib/authlib.php",
        "lib/classes/access", "lib/classes/context", "lib/classes/context.php", "lib/classes/context_helper.php",
        "lib/tests/accesslib_test.php", "lib/tests/accesslib_has_capability_test.php", "lib/tests/authlib_test.php",
        "lib/tests/context", "lib/tests/context_test.php", "lib/tests/context_helper_test.php",
        "lib/tests/context_block_test.php", "lib/tests/customcontext_test.php",
    ],
    "course": ["course", "enrol", "lib/enrollib.php", "lib/modinfolib.php", "lib/tests/modinfolib_test.php"],
    # Closest to prerequisite checks; Moodle has no advising module.
    "advising": ["availability", "completion", "lib/completionlib.php", "lib/tests/completionlib_test.php"],
    "assignment": ["mod/assign"],
    "grading": ["grade", "lib/grade", "lib/gradelib.php", "lib/tests/gradelib_test.php"],
    # New module, no Moodle equivalent. Closest reference: learning plans and competencies.
    "planning": ["admin/tool/lp", "competency"],
}
SKIP_DIRS = {"lang", "vendor", "node_modules"}
BUILD_PARTS = ("/amd/build/", "/yui/build/")  # generated copies of amd/src and yui/src
CCN_LIMIT = 10  # a function above this counts as "complex"


def third_party(root):
    """Paths listed in Moodle's thirdpartylibs.xml files (relative to public/)."""
    out = []
    for dirpath, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in {"node_modules", ".git"}]
        if "thirdpartylibs.xml" in files:
            with open(os.path.join(dirpath, "thirdpartylibs.xml"), encoding="utf-8") as fh:
                for loc in re.findall(r"<location>\s*([^<]+?)\s*</location>", fh.read()):
                    rel = os.path.relpath(os.path.join(dirpath, loc), root).replace("\\", "/")
                    out.append(rel.rstrip("/"))
    return out


def module_files(root, paths, tp):
    for p in paths:
        full = os.path.join(root, p)
        if os.path.isfile(full):
            yield p
            continue
        for dirpath, dirs, files in os.walk(full):
            dirs[:] = sorted(d for d in dirs if d not in SKIP_DIRS)
            for f in sorted(files):
                rel = os.path.relpath(os.path.join(dirpath, f), root).replace("\\", "/")
                if any(b in "/" + rel for b in BUILD_PARTS):
                    continue
                if any(rel == t or rel.startswith(t + "/") for t in tp):
                    continue
                yield rel


def is_test(rel):
    return "/tests/" in "/" + rel


def cloc_by_file(root, rels):
    """cloc code lines and language for each file."""
    with tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False, encoding="utf-8") as fh:
        fh.write("\n".join(os.path.join(root, r) for r in rels))
        listfile = fh.name
    try:
        raw = subprocess.run(["cloc", "--json", "--by-file", "--quiet", "--list-file=" + listfile],
                             capture_output=True, text=True, check=True).stdout
    finally:
        os.remove(listfile)
    data = json.loads(raw) if raw.strip() else {}
    res = {}
    for r in rels:
        e = data.get(os.path.join(root, r))
        if e:
            res[r] = (e["language"], e["code"])
    return res


def lang_group(lang):
    if lang == "PHP":
        return "php"
    if lang == "JavaScript":
        return "js"
    if lang == "Mustache":
        return "mustache"
    return "other"


def main():
    root = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else "results"
    os.makedirs(out, exist_ok=True)
    tp = third_party(root)
    seen = {}
    mod_rows, top_rows, fn_rows = [], [], []
    for mod, paths in MODULES.items():
        for p in paths:
            if not os.path.exists(os.path.join(root, p)):
                sys.exit(f"missing path for {mod}: {p}")
        rels = list(dict.fromkeys(module_files(root, paths, tp)))
        for r in rels:
            if r in seen:
                sys.exit(f"{r} is counted in both {seen[r]} and {mod}")
            seen[r] = mod
        counts = cloc_by_file(root, rels)
        src = {"php": 0, "js": 0, "mustache": 0, "other": 0}
        src_files = test_files = test_lines = feat_lines = 0
        funcs = []
        for rel, (lang, n) in sorted(counts.items()):
            if is_test(rel):
                if rel.endswith(".feature"):
                    feat_lines += n
                elif lang == "PHP":
                    test_lines += n
                    test_files += 1
                continue
            src[lang_group(lang)] += n
            src_files += 1
            if lang == "PHP":
                info = lizard.analyze_file(os.path.join(root, rel))
                for f in info.function_list:
                    funcs.append((rel, (f.long_name.split() or ["<anonymous>"])[0],
                                  f.cyclomatic_complexity, f.nloc, f.start_line))
        ccn = [f[2] for f in funcs]
        over = sum(c > CCN_LIMIT for c in ccn)
        mod_rows.append({
            "module": mod, "paths": " ".join(paths),
            "src_files": src_files,
            "src_php_lines": src["php"], "src_js_lines": src["js"],
            "src_mustache_lines": src["mustache"], "src_other_lines": src["other"],
            "src_all_lines": sum(src.values()),
            "test_php_files": test_files, "test_php_lines": test_lines,
            "test_to_code_ratio": round(test_lines / src["php"], 3) if src["php"] else 0,
            "behat_feature_lines": feat_lines,
            "functions": len(funcs),
            "ccn_mean": round(statistics.mean(ccn), 2),
            "ccn_median": statistics.median(ccn),
            "ccn_max": max(ccn),
            "functions_over_limit": over,
            "pct_over_limit": round(100 * over / len(ccn), 1),
        })
        for rank, f in enumerate(sorted(funcs, key=lambda x: -x[2])[:5], 1):
            top_rows.append({"module": mod, "rank": rank, "function": f[1], "file": f[0],
                             "line": f[4], "ccn": f[2], "nloc": f[3]})
        fn_rows += [{"module": mod, "file": f[0], "function": f[1], "ccn": f[2], "nloc": f[3]} for f in funcs]
        r = mod_rows[-1]
        print(f"{mod}: {r['src_php_lines']} PHP / {r['src_all_lines']} all src, "
              f"{test_lines} test lines, {len(funcs)} functions, mean CCN {r['ccn_mean']}")

    for name, rows in (("modules.csv", mod_rows), ("top5.csv", top_rows), ("functions.csv", fn_rows)):
        with open(os.path.join(out, name), "w", newline="", encoding="utf-8") as fh:
            w = csv.DictWriter(fh, fieldnames=list(rows[0]))
            w.writeheader()
            w.writerows(rows)


if __name__ == "__main__":
    main()
