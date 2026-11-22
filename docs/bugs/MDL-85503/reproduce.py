#!/usr/bin/env python3
"""Reproduce MDL-85503 on the Docker Moodle 5.3.1 of infra/ (Python 3.8+, standard library only).

MDL-85503: "Course Category View - End users do not see categories for enrolled courses".
When the capability moodle/category:viewcourselist is not allowed for authenticated users, the front page
"List of categories" and "Combo list" (and /course/index.php) show nothing to a student, even though the student
is enrolled in courses of that category. An admin still sees everything.

Run from the repository root, with Moodle running and the sample data loaded:
    python docs/bugs/MDL-85503/reproduce.py
The script changes four settings, observes the pages as student1 (enrolled in CS101 and CS201) and as admin,
and puts every setting back at the end (also when it fails). Output is printed and saved to results.txt.
"""
import http.cookiejar
import json
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
COMPOSE = ["docker", "compose", "-f", str(ROOT / "infra" / "docker-compose.yml")]
COURSES = ["Introduction to Programming", "Data Structures", "Discrete Mathematics"]
CATEGORY = "Engineering"
COMBO, NAMES = "4", "2"  # frontpageloggedin: "Combo list", "List of categories"


def read_env():
    values = {}
    for line in (ROOT / "infra" / ".env").read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


ENV = read_env()
BASE = ENV.get("MOODLE_URL", "http://localhost:8000").rstrip("/")


def moodle_php(code):
    """Run PHP code inside the Moodle container (code is sent on stdin) and return its output."""
    header = "<?php\ndefine('CLI_SCRIPT', true);\nrequire('/var/www/moodle/config.php');\n"
    run = subprocess.run(COMPOSE + ["exec", "-T", "-u", "www-data", "moodle", "php"],
                         input=header + code, capture_output=True, text=True, encoding="utf-8")
    if run.returncode != 0:
        sys.exit("PHP in container failed:\n" + run.stdout + run.stderr)
    return run.stdout.strip()


def get_state():
    return json.loads(moodle_php("""
$role = $DB->get_record('role', ['shortname' => 'user'], '*', MUST_EXIST);
$ctx = context_system::instance()->id;
$perm = $DB->get_field('role_capabilities', 'permission',
    ['roleid' => $role->id, 'contextid' => $ctx, 'capability' => 'moodle/category:viewcourselist']);
echo json_encode(['enablemyhome' => get_config('core', 'enablemyhome'),
    'defaulthomepage' => get_config('core', 'defaulthomepage'),
    'frontpageloggedin' => get_config('core', 'frontpageloggedin'),
    'viewcourselist_override' => $perm === false ? null : (int) $perm]);
"""))


def set_state(state):
    """state: enablemyhome, defaulthomepage, frontpageloggedin and viewcourselist_override
    (the permission of role "user" at system level: 1 = allow (Moodle default), -1 = prevent)."""
    moodle_php("""
$s = json_decode('%s', true);
foreach (['enablemyhome', 'defaulthomepage', 'frontpageloggedin'] as $name) {
    set_config($name, $s[$name]);
}
$role = $DB->get_record('role', ['shortname' => 'user'], '*', MUST_EXIST);
$ctx = context_system::instance()->id;
assign_capability('moodle/category:viewcourselist', (int) $s['viewcourselist_override'], $role->id, $ctx, true);
purge_all_caches();
""" % json.dumps(state))


class Browser:
    def __init__(self, username, password):
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        page = self.opener.open(BASE + "/login/index.php").read().decode()
        token = re.search(r'name="logintoken" value="(\w+)"', page).group(1)
        body = urllib.parse.urlencode({"username": username, "password": password, "logintoken": token}).encode()
        self.opener.open(BASE + "/login/index.php", body).read()

    def get(self, path):
        try:
            response = self.opener.open(BASE + path)
            return response.status, response.read().decode()
        except urllib.error.HTTPError as error:
            return error.code, ""


def names_in(html):
    found = [n for n in [CATEGORY] + COURSES if n in html]
    return ", ".join(found) if found else "(nothing)"


def observe(browser, section_class):
    """What the user sees in the front page section and on /course/index.php."""
    _, home = browser.get("/")
    start = home.find(section_class)
    section = names_in(home[start:start + 12000]) if start >= 0 else "(section not shown)"
    status, index = browser.get("/course/index.php")
    category_page = names_in(index) if status == 200 else "HTTP %d" % status
    return section, category_page


SCENARIOS = [
    ("1. Default permission (allowed), Combo list", COMBO, 1, "frontpage-category-combo"),
    ("2. viewcourselist prevented for authenticated users, Combo list", COMBO, -1, "frontpage-category-combo"),
    ("3. viewcourselist prevented for authenticated users, List of categories", NAMES, -1, "frontpage-category-names"),
]


def main():
    original = get_state()
    lines = ["MDL-85503 on Moodle %s (student1 is enrolled in CS101 and CS201; admin is the site administrator)" % BASE, ""]
    try:
        for title, layout, override, section_class in SCENARIOS:
            set_state({"enablemyhome": "1", "defaulthomepage": "0", "frontpageloggedin": layout,
                       "viewcourselist_override": override})
            lines.append(title)
            # New logins for every scenario: Moodle caches a user's permissions in the session.
            for who, browser in (("student1", Browser("student1", ENV["SAMPLE_USER_PASSWORD"])),
                                 ("admin", Browser("admin", ENV["MOODLE_ADMIN_PASSWORD"]))):
                section, category_page = observe(browser, section_class)
                lines.append("  %-9s front page section: %-62s /course/index.php: %s" % (who, section, category_page))
            lines.append("")
    finally:
        set_state(original)
    lines.append("Settings restored to: " + json.dumps(original))
    output = "\n".join(lines)
    print(output)
    (Path(__file__).parent / "results.txt").write_text(output + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
