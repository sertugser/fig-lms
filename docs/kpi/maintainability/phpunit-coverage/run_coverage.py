#!/usr/bin/env python3
"""Run the PHPUnit tests of one module in the PHPUnit container and collect line coverage (MAINT-05).

Usage (from the repository root, PHPUnit container running and initialised, see README.md):
    python docs/kpi/maintainability/phpunit-coverage/run_coverage.py <module> [--hours 3]
    <module> is a key of MODULES in measure.py: identity, course, advising, assignment, grading, planning

Writes to docs/kpi/maintainability/results/phpunit/<module>-*: clover.xml, junit.xml, run.txt (command, time, test counts).
Standard library only. The module -> folder mapping is read from MODULES in measure.py, so both measurements use the same folders.
"""
import argparse
import ast
import re
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
RESULTS = ROOT / "docs" / "kpi" / "maintainability" / "results" / "phpunit"
COMPOSE = ["docker", "compose", "-f", str(ROOT / "infra" / "docker-compose.yml"),
           "-f", str(ROOT / "infra" / "docker-compose.phpunit.yml")]
SERVICE = "moodle-phpunit"


def load_modules():
    """MODULES from measure.py, read without importing it (so lizard and cloc are not needed here)."""
    tree = ast.parse((HERE.parent / "measure.py").read_text(encoding="utf-8"))
    for node in tree.body:
        if isinstance(node, ast.Assign) and getattr(node.targets[0], "id", "") == "MODULES":
            return ast.literal_eval(node.value)
    sys.exit("MODULES not found in measure.py")


def in_container(args, stdin=None, check=True, **kw):
    return subprocess.run(COMPOSE + ["exec", "-T", "-u", "root", "-e", "COMPOSER_ALLOW_SUPERUSER=1", SERVICE] + args,
                          input=stdin, capture_output=True, text=True, encoding="utf-8", check=check, **kw)


def is_test_path(p):
    return "/tests/" in "/" + p + "/" or p.endswith("/tests")


def build_config(base_xml, paths):
    """Moodle's generated phpunit.xml, with the test suite and the coverage source replaced for this module."""
    tests, sources = [], []
    for p in paths:
        is_file = p.endswith(".php")
        if is_test_path(p):
            tests.append(f'<file>public/{p}</file>' if is_file else f'<directory suffix="_test.php">public/{p}</directory>')
        elif is_file:
            sources.append(f'<file>public/{p}</file>')
        else:
            tests.append(f'<directory suffix="_test.php">public/{p}</directory>')
            sources.append(f'<directory suffix=".php">public/{p}</directory>')
    suites = "<testsuites>\n<testsuite name=\"module\">\n" + "\n".join(tests) + "\n</testsuite>\n</testsuites>"
    source = "<source>\n<include>\n" + "\n".join(sources) + "\n</include>\n</source>"
    xml = re.sub(r"<testsuites>.*?</testsuites>", lambda m: suites, base_xml, flags=re.S)
    xml = re.sub(r"<source>.*?</source>", lambda m: source, xml, flags=re.S)
    # Moodle's file makes warnings fatal; with coverage on, PHPUnit warns about #[CoversClass] targets outside the
    # coverage source. The tests themselves still pass or fail as usual.
    xml = xml.replace('failOnWarning="true"', 'failOnWarning="false"').replace('failOnDeprecation="true"', 'failOnDeprecation="false"')
    if "<source>" not in xml:
        xml = xml.replace("</phpunit>", source + "\n</phpunit>")
    return xml


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("module")
    ap.add_argument("--hours", type=float, default=3, help="stop the run after this many hours")
    args = ap.parse_args()
    modules = load_modules()
    if args.module not in modules:
        sys.exit(f"unknown module {args.module!r}; choose one of: {', '.join(modules)}")
    RESULTS.mkdir(parents=True, exist_ok=True)

    base = in_container(["cat", "/var/www/moodle/phpunit.xml"]).stdout
    config = build_config(base, modules[args.module])
    in_container(["sh", "-c", f"cat > /var/www/moodle/phpunit-{args.module}.xml"], stdin=config)

    cmd = (f"cd /var/www/moodle && timeout {int(args.hours * 3600)} php vendor/bin/phpunit "
           f"--configuration phpunit-{args.module}.xml --do-not-cache-result "
           f"--coverage-clover /results/{args.module}-clover.xml --log-junit /results/{args.module}-junit.xml")
    print("running:", cmd, flush=True)
    start = time.time()
    run = in_container(["sh", "-c", cmd + " > /results/%s-phpunit.log 2>&1" % args.module], check=False)
    minutes = (time.time() - start) / 60
    tail = in_container(["sh", "-c", f"tail -n 12 /results/{args.module}-phpunit.log"], check=False).stdout
    summary = (f"module: {args.module}\nexit code: {run.returncode} (0 or 1 is normal: 1 means PHPUnit printed warnings or a test failed, see the output below; 124 = stopped by the time limit)\n"
               f"minutes: {minutes:.1f}\ncommand: {cmd}\n\n--- end of PHPUnit output ---\n{tail}")
    (RESULTS / f"{args.module}-run.txt").write_text(summary, encoding="utf-8")
    print(summary)


if __name__ == "__main__":
    main()
