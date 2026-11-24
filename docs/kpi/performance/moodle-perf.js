// Moodle performance baseline (#15). k6 script; see docs/kpi/performance.md for how to run it.
//
//   k6 run -e MODE=baseline -e RUN=1 docs/kpi/performance/moodle-perf.js
//   k6 run -e MODE=load     -e RUN=1 docs/kpi/performance/moodle-perf.js
//
// Environment variables:
//   BASE_URL              default http://localhost:8000
//   SAMPLE_USER_PASSWORD  password of the sample users (infra/.env)
//   MOODLE_WS_TOKEN       REST token (infra/.env)
//   MODE                  baseline | load
//   RUN                   run label used in the result file name
//   OUT_DIR               default docs/kpi/performance/results
import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8000';
const PASSWORD = __ENV.SAMPLE_USER_PASSWORD;
const TOKEN = __ENV.MOODLE_WS_TOKEN;
const MODE = __ENV.MODE || 'baseline';
const RUN = __ENV.RUN || '1';
const OUT_DIR = __ENV.OUT_DIR || 'docs/kpi/performance/results';

// Sample data from infra/sample-data (#8).
const COURSE_ID = 2;          // CS101 Introduction to Programming
const ASSIGN_CMID = 1;        // "Assignment 1" in CS101
const WARMUP = 5;             // requests per page that are not measured
const SAMPLES = 30;           // measured requests per page and run

const tLogin = new Trend('perf_login', true);
const tCourse = new Trend('perf_course_page', true);
const tAssign = new Trend('perf_assign_page', true);
const tGrader = new Trend('perf_gradebook', true);
const tRest = new Trend('perf_rest_get_courses', true);
const tLoadCourse = new Trend('perf_load_course_page', true);
const errors = new Rate('perf_errors');
const loadRequests = new Counter('perf_load_requests');

export const options = MODE === 'load'
  ? {
      scenarios: {
        load: { executor: 'constant-vus', vus: 20, duration: '60s', gracefulStop: '10s' },
      },
      summaryTrendStats: ['min', 'med', 'avg', 'p(90)', 'p(95)', 'max', 'count'],
    }
  : {
      scenarios: {
        baseline: { executor: 'shared-iterations', vus: 1, iterations: 1, maxDuration: '15m' },
      },
      summaryTrendStats: ['min', 'med', 'avg', 'p(90)', 'p(95)', 'max', 'count'],
    };

export function setup() {
  if (!PASSWORD || !TOKEN) {
    fail('Set SAMPLE_USER_PASSWORD and MOODLE_WS_TOKEN (see docs/kpi/performance.md).');
  }
  const res = http.get(`${BASE}/login/index.php`);
  if (res.status !== 200) {
    fail(`Moodle not reachable at ${BASE} (HTTP ${res.status}).`);
  }
}

/** Log in with a fresh cookie jar. Returns {jar, ms}: ms = POST login + GET of the page it redirects to. */
function login(username) {
  const jar = new http.CookieJar();
  const page = http.get(`${BASE}/login/index.php`, { jar, tags: { name: 'login_form' } });
  const m = page.body.match(/name="logintoken" value="([^"]+)"/);
  if (!m) {
    fail('logintoken not found on the login page');
  }
  const post = http.post(`${BASE}/login/index.php`,
    { username, password: PASSWORD, logintoken: m[1], anchor: '' },
    { jar, redirects: 0, tags: { name: 'login_post' } });
  let ms = post.timings.duration;
  let ok = post.status === 303 || post.status === 302;
  let location = post.headers.Location || post.headers.location || '';
  // Follow redirects until a page that is not the login page (normally /login/index.php?testsession=… then /my/).
  for (let i = 0; ok && location && i < 5; i++) {
    const url = location.startsWith('http') ? location : `${BASE}${location}`;
    const r = http.get(url, { jar, redirects: 0, tags: { name: 'login_redirect' } });
    ms += r.timings.duration;
    location = (r.status === 303 || r.status === 302) ? (r.headers.Location || r.headers.location || '') : '';
    ok = ok && (r.status === 200 || location !== '');
    if (r.status === 200) {
      ok = ok && !r.url.includes('/login/');
    }
  }
  errors.add(!ok);
  if (!ok) {
    fail(`login failed for ${username}`);
  }
  return { jar, ms };
}

/** GET a page as a logged-in user; counts it as an error if it is not 200 or lands on the login page. */
function page(jar, path, name, mustContain) {
  const res = http.get(`${BASE}${path}`, { jar, tags: { name } });
  const ok = check(res, {
    [`${name} 200`]: (r) => r.status === 200,
    [`${name} not login`]: (r) => !r.url.includes('/login/'),
    [`${name} shows "${mustContain}"`]: (r) => r.body.includes(mustContain),
  });
  errors.add(!ok);
  return res.timings.duration;
}

function rest() {
  const res = http.post(`${BASE}/webservice/rest/server.php`,
    { wstoken: TOKEN, wsfunction: 'core_course_get_courses', moodlewsrestformat: 'json' },
    { tags: { name: 'rest_get_courses' } });
  const ok = check(res, {
    'rest 200': (r) => r.status === 200,
    'rest returns courses': (r) => Array.isArray(r.json()) && r.json().length >= 4,
  });
  errors.add(!ok);
  return res.timings.duration;
}

/** Warm up, then measure SAMPLES requests. */
function measure(trend, fn) {
  for (let i = 0; i < WARMUP; i++) fn();
  for (let i = 0; i < SAMPLES; i++) trend.add(fn());
}

let vuJar = null;

export default function () {
  if (MODE === 'load') {
    // Each virtual user logs in once as one of student1..student5, then opens the course page every second.
    if (!vuJar) {
      vuJar = login(`student${((__VU - 1) % 5) + 1}`).jar;
    }
    tLoadCourse.add(page(vuJar, `/course/view.php?id=${COURSE_ID}`, 'load_course_page', 'Assignment 1'));
    loadRequests.add(1);
    sleep(1);
    return;
  }

  // Baseline: one user at a time.
  measure(tLogin, () => login('student1').ms);
  const student = login('student1').jar;
  const teacher = login('teacher1').jar;
  measure(tCourse, () => page(student, `/course/view.php?id=${COURSE_ID}`, 'course_page', 'Assignment 1'));
  measure(tAssign, () => page(student, `/mod/assign/view.php?id=${ASSIGN_CMID}`, 'assign_page', 'Submission status'));
  measure(tGrader, () => page(teacher, `/grade/report/grader/index.php?id=${COURSE_ID}`, 'gradebook', 'Zeynep'));
  measure(tRest, rest);
}

export function handleSummary(data) {
  const pick = (name) => {
    const m = data.metrics[name];
    return m ? m.values : null;
  };
  const out = {
    mode: MODE,
    run: RUN,
    finished: new Date().toISOString(),
    base_url: BASE,
    metrics: {
      perf_login: pick('perf_login'),
      perf_course_page: pick('perf_course_page'),
      perf_assign_page: pick('perf_assign_page'),
      perf_gradebook: pick('perf_gradebook'),
      perf_rest_get_courses: pick('perf_rest_get_courses'),
      perf_load_course_page: pick('perf_load_course_page'),
      perf_load_requests: pick('perf_load_requests'),
      perf_errors: pick('perf_errors'),
      http_req_failed: pick('http_req_failed'),
    },
  };
  const file = `${OUT_DIR}/${MODE}-run${RUN}.json`;
  const lines = Object.entries(out.metrics)
    .filter(([, v]) => v && v.med !== undefined)
    .map(([k, v]) => `${k.padEnd(24)} med ${v.med.toFixed(1)} ms   p95 ${v['p(95)'].toFixed(1)} ms   n=${v.count}`);
  return {
    [file]: JSON.stringify(out, null, 2),
    stdout: `\n${MODE} run ${RUN} -> ${file}\n${lines.join('\n')}\n` +
      `errors: ${out.metrics.perf_errors ? (out.metrics.perf_errors.rate * 100).toFixed(2) : 0}%\n`,
  };
}
