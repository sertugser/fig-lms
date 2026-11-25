#!/usr/bin/env python3
"""Rough coupling count between the five Moodle areas we rewrite (Moodle 5.3.1).

For every PHP file of area A (tests excluded) we check whether it
  (1) names a database table that belongs to area B ($DB->...('table') or {table} in SQL), or
  (2) calls a well-known function or class of area B (list in API below).
A file counts once per (A, B) pair. The result is "how many files of A reach directly into B".

This is a heuristic (regular expressions, not a PHP parser). Use the numbers to show the shape
(who depends on whom, roughly how much), not as exact values.

Usage: python3 measure_coupling.py <path to moodle/public at tag v5.3.1>
"""
import collections
import os
import re
import sys

AREAS = {
    'identity': ['auth', 'login', 'admin/roles', 'lib/accesslib.php', 'lib/authlib.php'],
    'course': ['course', 'enrol', 'lib/enrollib.php'],
    'advising': ['availability', 'completion', 'lib/completionlib.php'],
    'assignment': ['mod/assign'],
    'grading': ['grade', 'lib/grade', 'lib/gradelib.php'],
}

# Core tables (lib/db/install.xml) assigned to an area by hand. Plugin tables come from each area's db/install.xml.
CORE_TABLES = {
    'identity': ['user', 'role', 'role_assignments', 'role_capabilities', 'role_allow_assign', 'context', 'capabilities'],
    'course': ['course', 'course_categories', 'enrol', 'user_enrolments', 'course_modules', 'course_sections'],
    'advising': ['course_completions', 'course_completion_criteria', 'course_completion_crit_compl',
                 'course_modules_completion', 'course_completion_aggr_methd'],
    'grading': ['grade_items', 'grade_grades', 'grade_categories', 'grade_outcomes'],
}

API = {
    'identity': r'\b(has_capability|require_capability|require_login|role_assign|get_role_users)\s*\(',
    'course': r'\b(is_enrolled|enrol_get_plugin|enrol_get_instances|get_enrolled_users|get_enrolled_sql)\s*\(',
    'advising': r'\bnew\s+\\?completion_info\b|core_availability\\info|completionlib\.php',
    'grading': r'\b(grade_update|grade_get_grades|grade_regrade_final_grades)\s*\(|\bnew\s+\\?grade_item\b|gradelib\.php',
    'assignment': r'\bnew\s+\\?assign\s*\(|mod/assign/locallib\.php',
}


def area_of(rel):
    for area, prefixes in AREAS.items():
        if any(rel == p or rel.startswith(p + '/') for p in prefixes):
            return area
    return None


def main(root):
    files = collections.defaultdict(list)
    owner = {}
    for dirpath, _, names in os.walk(root):
        rel_dir = os.path.relpath(dirpath, root)
        if '/tests' in '/' + rel_dir or 'vendor' in rel_dir or 'node_modules' in rel_dir:
            continue
        for name in names:
            rel = os.path.normpath(os.path.join(rel_dir, name))
            area = area_of(rel)
            if not area:
                continue
            if name.endswith('.php'):
                files[area].append(rel)
            elif name == 'install.xml' and rel_dir.endswith('db'):
                for table in re.findall(r'<TABLE NAME="([a-z0-9_]+)"', open(os.path.join(root, rel)).read()):
                    owner[table] = area
    for area, tables in CORE_TABLES.items():
        for table in tables:
            owner[table] = area

    table_ref = re.compile(r"\$DB->\w+\(\s*['\"]([a-z0-9_]+)['\"]|\{([a-z0-9_]+)\}")
    reach = collections.Counter()
    print('| Area | PHP files | Lines | `$DB->` calls |')
    print('|---|---|---|---|')
    for area in AREAS:
        lines = db_calls = 0
        for rel in files[area]:
            src = open(os.path.join(root, rel), errors='ignore').read()
            lines += src.count('\n')
            db_calls += src.count('$DB->')
            tables = {a or b for a, b in table_ref.findall(src)}
            for other in AREAS:
                if other == area:
                    continue
                if any(owner.get(t) == other for t in tables) or re.search(API[other], src):
                    reach[(area, other)] += 1
        print(f'| {area} | {len(files[area])} | {lines} | {db_calls} |')
    print()
    print('Files of A that reach directly into B (tables or functions):')
    print()
    print('| A \\ B | ' + ' | '.join(AREAS) + ' |')
    print('|---|' + '---|' * len(AREAS))
    for area in AREAS:
        cells = ['-' if area == other else str(reach[(area, other)]) for other in AREAS]
        print(f'| {area} | ' + ' | '.join(cells) + ' |')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
