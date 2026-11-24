| KPI | What | Runs | Median per run (ms) | p95 per run (ms) | **Median** (ms) | **p95** (ms) |
|---|---|---:|---|---|---:|---:|
| PERF-01 | Login (POST + redirect to dashboard) | 3 | 90.1 / 90.3 / 87.7 | 121.6 / 115.8 / 112.5 | **90.1** | **115.8** |
| PERF-02 | Course page (CS101, student) | 3 | 36.7 / 37.6 / 38.4 | 40.4 / 39.5 / 44.0 | **37.6** | **40.4** |
| PERF-03 | Assignment page (submission status, student) | 3 | 51.7 / 49.7 / 49.9 | 71.2 / 54.5 / 59.2 | **49.9** | **59.2** |
| PERF-04 | Gradebook (grader report, teacher) | 3 | 46.3 / 46.9 / 48.0 | 51.1 / 49.9 / 54.8 | **46.9** | **51.1** |
| PERF-05 | REST core_course_get_courses | 3 | 11.8 / 12.0 / 13.2 | 15.3 / 16.9 / 15.6 | **12.0** | **15.6** |
| PERF-06 | Course page, 20 concurrent users | 3 | 73.8 / 83.1 / 80.3 | 148.6 / 166.0 / 158.5 | **80.3** | **158.5** |

- load run 1: 1098 course page requests (18.0/s), errors 0.00%, failed HTTP requests 0.00%
- load run 2: 1078 course page requests (17.9/s), errors 0.00%, failed HTTP requests 0.00%
- load run 3: 1078 course page requests (17.8/s), errors 0.00%, failed HTTP requests 0.00%
- baseline run 1: errors 0.00%
- baseline run 2: errors 0.00%
- baseline run 3: errors 0.00%
