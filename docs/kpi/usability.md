# Usability KPIs: Moodle baseline

## Objective

Measure how many interactions Moodle 5.3.1 requires for five workflows. Use the same roles, start and finish states, fixture conditions, and counting rules for Fig LMS. Each result represents one observed run.

## Environment

All runs used Docker Moodle 5.3.1 (Build 20261112) on the [Issue #8 stack](../../infra/README.md). The assisted runs used English Boost at 1440 × 1000. Playwright Core 1.65.1 drove fresh, sandboxed headless Chrome 157.0.8103.79 through visible controls and normal sample-account login. Hosted and native browser controls were unavailable. The local test browser did not use an existing browser profile.

Assisted writes used `UX16-BASELINE`. Human write runs used the separate `UX16-MANUAL` fixture. Viewing the existing CS101 grade was read-only. The human tester reported a narrow responsive viewport for FLOW-01 and FLOW-02 and desktop layout for FLOW-03 and FLOW-04. Exact dimensions were not supplied. The tester reported that the relevant click targets appeared unchanged in the narrow layout. This observation does not establish equivalent layout, navigation, or counts across viewports.

## Measurement methodology

Start each flow from the fully loaded Moodle **Dashboard**, already authenticated in the named role. For Fig LMS, use the equivalent authenticated landing page. Start counting at the first task action, stop at the first visible proof of completion, and exclude authentication, fixture preparation, landing-page load, passive observation, and incidental scrolling. Use normal visible controls and match locale, theme, and viewport when comparing paired runs. A failed attempt keeps its own log and has no completed-workflow total.

| KPI | Unit | Counting rule |
| --- | --- | --- |
| Clicks | clicks per completed workflow | Count one per deliberate pointer activation of a link, button, menu item, or selection. Count an opening click and a separate option click individually. A double-click that performs one activation contributes one click. Two deliberate activations contribute two. Keyboard activation and text entry contribute zero clicks. Record the input method when it differs from the standard route. |
| Steps | actions per completed workflow | Count one per purposeful activation or logical input operation. Consecutive typing or pasting into one field counts as one entry step. An intentional return to revise a field is a new step. File picker opening, file selection and any separate confirmation are separate actions. A shortcut that activates one control is one step. |
| Page loads | documents per completed workflow | Count each newly loaded main-frame document after the starting page, including refreshes and new tabs opened by the flow. Count the final rendered document after redirects once. Exclude AJAX updates, dialogs, frame loads, same-document URL changes, and back/forward restoration without a new document. Attribute automatic main-frame loads to the triggering action. The assisted runs counted Chromium main-frame document commits from CDP and excluded back/forward cache restoration. The human records retain tester-confirmed document totals without inferring action-level attribution. For human repeat runs, retain the browser Network log, filter to documents, and disregard redirect responses. |
| Form fields | unique controls per completed workflow | Count each distinct visible form control whose value the participant enters, selects, changes, or populates with a file. Count a radio group once, each independently changed checkbox once, and a repeated edit of one field once. Include a search input when the user enters a query. Exclude untouched defaults, hidden or prefilled values, navigation-only selections, and save or confirmation buttons. |

Record each action and its evidence in the [raw interaction record](usability/measurements.md). Sum the click, step, and page-load increments. Count unique field identifiers across the successful run. Report one run as an observation, and record configuration or screen-state differences before comparing runs.

Pointer activations used normal Playwright clicks. Field fills represent one logical entry using keyboard focus, with zero pointer activations. Edit mode and the activity chooser change navigation state, so they count as actions without adding a form field. The FLOW-05 catalogue URL represents one browser address-bar navigation, with zero pointer clicks. Course-level tours count when they cover an in-flow control or the finish state. The Dashboard was prepared with its welcome tour closed before counting.

## Moodle results — assisted observations

| Flow | Clicks | Steps | Page loads | Fields | Status | Evidence |
| --- | ---: | ---: | ---: | ---: | --- | --- |
| FLOW-01 — Submit an assignment | 6 | 7 | 6 | 1 | Complete | [Actions and capture](usability/measurements.md#flow-01) |
| FLOW-02 — View own assignment grade | 3 | 3 | 2 | 0 | Complete | [Actions and capture](usability/measurements.md#flow-02) |
| FLOW-03 — Create an assignment | 8 | 9 | 3 | 1 | Complete | [Actions and capture](usability/measurements.md#flow-03) |
| FLOW-04 — Grade a submission | 6 | 7 | 4 | 1 | Complete | [Actions and capture](usability/measurements.md#flow-04) |
| FLOW-05 — Self-enrol in a course | 4 | 6 | 4 | 1 | Complete, URL-assisted | [Actions and capture](usability/measurements.md#flow-05) |

The five rows above preserve the Playwright observations and their original tour states. Those runs preceded the human walkthroughs.

## Moodle results — human-operated observations

| Flow | Clicks | Steps | Page loads | Fields | Viewport | Status and verified finish |
| --- | ---: | ---: | ---: | ---: | --- | --- |
| [FLOW-01](usability/measurements.md#manual-flow-01) | 6 | 7 | 6 | 1 | Narrow responsive | Complete, submitted for grading |
| [FLOW-02](usability/measurements.md#manual-flow-02) | 2 | 2 | 2 | 0 | Narrow responsive | Complete, Assignment 1 grade 85/100 |
| [FLOW-03](usability/measurements.md#manual-flow-03) | 8 | 9 | 4 | 1 | Desktop layout | Complete, assignment created |
| [FLOW-04](usability/measurements.md#manual-flow-04) | 7 | 8 | 4 | 1 | Desktop layout | Complete, student4 graded 75/100 |
| [FLOW-05](usability/measurements.md#manual-flow-05) | Pending | Pending | Pending | Pending | Unreported | Blocked at course discovery |

Each completed row is one tester-confirmed human run. No human screenshots or action-level network traces were supplied. For FLOW-05, the unsuccessful logo click is recorded separately from completed-workflow totals.

### Differences from the assisted runs

- **FLOW-01:** All four totals match. The human run used a narrow layout and excluded two HTTP 303 responses. Matching totals alone do not establish that the desktop human route will have the same counts.
- **FLOW-02:** The human route had one fewer click and step. It went directly from the course to **Grades** without the tour dismissal recorded in the assisted run. Both runs had two document loads. The human count excludes one HTTP 303 response.
- **FLOW-03:** The human run had one additional document load, with the same click, step, and field totals. The tester confirmed the separate **Add** button and the name-field focus click. The course, creation-form, and saved-assignment access-log responses are consistent with four document loads. The [sanitized trace](usability/measurements.md#manual-flow-03) includes a second successful course-page request. The available records do not establish which action caused it or the tour state.
- **FLOW-04:** The human run had one additional click and step. The tester clicked the grade field to focus it before entering 75. The assisted run filled the field without a pointer activation.
- **FLOW-05:** The assisted run reached the catalogue by URL. The human run attempted visible navigation and did not reach enrolment. The results describe different routes.

The four completed human runs meet the workflow-count threshold and form the mixed-viewport baseline. The fixtures, routes, and counting rules support repeat measurement. Exact historical viewport dimensions and FLOW-03’s complete action sequence are unavailable. If a future comparison requires a uniform desktop viewport, measure both systems under those conditions and retain the action-to-document trace.

## Usability findings

| Flow and step | Observation | Friction and proposed improvement |
| --- | --- | --- |
| FLOW-01, steps 5–7 | **Save changes** produced a draft. **Submit assignment** opened another page with **Continue**. | The student must distinguish saving from submitting and then confirm on a separate page. Keep draft and final submission distinct and use an inline confirmation for final submission. |
| FLOW-02, step 2 | A tour covered course navigation and required **Got it** before opening Grades. | A student checking a grade must handle onboarding before reaching it. Offer optional, nonblocking guidance. |
| FLOW-03, steps 2, 4–7 | The teacher skipped a blocking tour and went through **Add content**, **Activity or resource**, **Assignment**, and then **Add**. | Several selections precede the name form. Provide a direct assignment entry point with nonblocking guidance and open the name form after selecting Assignment. |
| FLOW-04, steps 4–5 | The roster exposed grading through a row menu separate from submission status. | A teacher must find the grade action in the correct row. Use a labelled Grade control and an inline grading panel. |
| FLOW-05, human discovery attempt | The learner found no visible catalogue route from Dashboard. One **figmoodle** logo click returned them to Dashboard, and they did not enrol. | Provide a clearly labelled course catalogue entry on Dashboard. This finding applies to the observed learner session. |
| FLOW-05, assisted step 6 | A course tour covered the success banner until dismissed. | Keep enrolment confirmation visible while displaying optional guidance. |

## Reproduction guide

Use the [human measurement guide](usability/manual-measurement.md) for the returning-user run. It describes the `UX16-MANUAL` fixtures and the existing CS101 grade for FLOW-02. Submission, creation, and grading succeeded on those fixtures, so recreate their starting states before a repeat. The original `UX16-BASELINE` course and its evidence are retained.

For the historical assisted routes, use the [interaction records](usability/measurements.md). Dashboard tours were closed during setup. FLOW-02 and FLOW-03 included course tours en route, FLOW-04 restarted after its course tour had been dismissed, and FLOW-05 included a tour dismissal at the finish. Treat differences in tour state as separate variants. The FLOW-05 assisted run navigated to the catalogue by URL. The separate human attempt started on Dashboard and stopped where visible course discovery was blocked. Any retry should use visible controls and record the route actually taken.

## Proposed Fig LMS targets

Targets are provisional design goals based on the recorded routes. The tuple order is clicks / steps / page loads / fields. Use the targets as design goals and compare routes with matching tour states and feature settings. FLOW-05’s target is conditional on providing a visible catalogue search on Dashboard.

| Flow | Proposed target | Rationale |
| --- | --- | --- |
| FLOW-01 | 6 / 7 / 5 / 1 | Keep draft saving, final submission, and the deliberate confirmation action. Show confirmation inline to remove one full document load. The confirmation still counts as one click and one step. |
| FLOW-02 | 2 / 2 / 2 / 0 | Reach the user report from Dashboard in two clicks by keeping the tour nonblocking. |
| FLOW-03 | 6 / 7 / 2 / 1 | Avoid a blocking tour and the separate chooser confirmation. Use an inline creation form while keeping the assignment name required. |
| FLOW-04 | 5 / 6 / 3 / 1 | Replace the row menu with a direct Grade control and open the editor inline to avoid a document transition. Keep the grade entry. |
| FLOW-05 | 3 / 4 / 3 / 1 | Put course catalogue search on the landing page and keep enrolment feedback unobscured. Keep the search field. |

## Handoff to Issue #3

Figures are in FLOW-01 through FLOW-05 order. The Moodle baseline column uses the four human-operated observations with their reported layouts. FLOW-05 is pending because the human run was blocked at course discovery. Keep the desktop Playwright observations as a separate reference. The handoff describes a mixed-viewport baseline.

| KPI | Category | Unit | Moodle baseline — human-operated | Proposed Fig LMS target | How measured | Owner |
| --- | --- | --- | --- | --- | --- | --- |
| Click count | Usability | clicks per completed workflow | 6, 2, 8, 7, pending | 6, 2, 6, 5, 3 | Tester-confirmed deliberate pointer activations, including recorded field-focus clicks | @SercanFurkan |
| Step count | Usability | actions per completed workflow | 7, 2, 9, 8, pending | 7, 2, 7, 6, 4 | Tester-confirmed purposeful actions, with one step per logical field entry | @SercanFurkan |
| Page loads | Usability | documents per completed workflow | 6, 2, 4, 4, pending | 5, 2, 2, 3, 3 | Tester-confirmed successful main-document loads after Dashboard, excluding redirect responses | @SercanFurkan |
| Form fields | Usability | unique controls per completed workflow | 1, 0, 1, 1, pending | 1, 0, 1, 1, 1 | Tester-confirmed count of distinct visible controls completed or changed | @SercanFurkan |
