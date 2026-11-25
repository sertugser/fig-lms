| Area | PHP files | Lines | `$DB->` calls |
|---|---|---|---|
| identity | 188 | 30771 | 262 |
| course | 567 | 114352 | 1018 |
| advising | 73 | 15695 | 127 |
| assignment | 290 | 65018 | 491 |
| grading | 320 | 63986 | 469 |

Files of A that reach directly into B (tables or functions):

| A \ B | identity | course | advising | assignment | grading |
|---|---|---|---|---|---|
| identity | - | 9 | 0 | 0 | 0 |
| course | 162 | - | 42 | 0 | 24 |
| advising | 14 | 18 | - | 0 | 5 |
| assignment | 40 | 12 | 6 | - | 3 |
| grading | 115 | 62 | 1 | 0 | - |
