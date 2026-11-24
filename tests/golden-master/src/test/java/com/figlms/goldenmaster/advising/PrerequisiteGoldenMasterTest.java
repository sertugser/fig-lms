package com.figlms.goldenmaster.advising;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.figlms.goldenmaster.FigBackendClient;
import com.figlms.goldenmaster.JsonDiff;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * First golden master test (#13): the course prerequisite check gives the same result in Moodle and in the new system.
 *
 * <p>Moodle cannot answer "may this student enrol in CS201?" (MDL-22648). The comparable Moodle fact is the student's
 * completion of the prerequisite CS101 ({@code core_completion_get_course_completion_status}), so the new system's
 * {@code GET /api/advising/prerequisite-check?course=CS201} must say {@code allowed = completed}, with
 * {@code missingPrerequisites = ["CS101"]} when not allowed. Scenarios and mapping: docs/srs/modules/advising.md,
 * section "Prerequisite rule (REQ-ADV-08 / REQ-ADV-09, used by #13)".
 *
 * <p>The Moodle side is read live when Moodle runs and {@code MOODLE_WS_TOKEN} is set, otherwise from the committed
 * snapshots (as in CI). The new system must run (see tests/golden-master/README.md); without it the test is skipped,
 * unless {@code -Dgolden.requireBackend=true} (CI), where it fails instead.
 */
@DisplayName("Golden master #13: prerequisite check, Moodle vs new system")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PrerequisiteGoldenMasterTest {

    static final String COURSE = "CS201";
    static final String PREREQUISITE = "CS101";
    static final String ENDPOINT = "/api/advising/prerequisite-check";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String[]> REPORT = new ArrayList<>();

    static MoodleCompletion moodle;
    static FigBackendClient backend;

    @BeforeAll
    static void connect() {
        backend = FigBackendClient.fromEnvironment();
        if (!backend.isUp()) {
            String message = "The new system is not running at " + backend.baseUrl()
                    + ". Start it first, or run ./run-prerequisite-golden-master.sh (see tests/golden-master/README.md).";
            if (Boolean.getBoolean("golden.requireBackend")) {
                fail(message);
            }
            Assumptions.abort(message);
        }
        moodle = MoodleCompletion.liveOrSnapshots();
    }

    @Test
    @Order(1)
    @DisplayName("Completed: student1 passed CS101 (85) -> may enrol in CS201")
    void completed() {
        compareDecision("Completed", "student1", "CS101 passed with 85");
    }

    @Test
    @Order(2)
    @DisplayName("Not completed: student5 did not submit CS101 -> may not enrol in CS201")
    void notCompleted() {
        compareDecision("Not completed", "student5", "CS101 not submitted");
    }

    @Test
    @Order(3)
    @DisplayName("Boundary: student3 failed CS101 (40 < pass 50) -> may not enrol in CS201")
    void boundaryFailed() {
        compareDecision("Boundary", "student3", "CS101 attempted, 40 < pass 50");
    }

    @Test
    @Order(4)
    @DisplayName("Boundary: student4 submitted CS101 but is not graded -> may not enrol in CS201")
    void boundaryNotGraded() {
        compareDecision("Boundary", "student4", "CS101 submitted, not graded");
    }

    /**
     * student3 asks about student1. In Moodle, {@code core_completion_get_course_completion_status} for another user
     * fails with {@code cannotviewreport} ({@code completion_can_view_data()} in completion/classes/external.php).
     * The harness token belongs to admin, so this Moodle result is the documented rule, not a live call.
     */
    @Test
    @Order(5)
    @DisplayName("Unauthorized: student3 asks about student1 -> denied")
    void unauthorized() {
        String moodleResult = "denied (cannotviewreport)";
        FigBackendClient.Response response = backend.get(ENDPOINT,
                Map.of("student", "student1", "course", COURSE), "student3");
        String newResult = response.status() == 403 ? "denied (403)" : "HTTP " + response.status();
        boolean same = response.status() == 403;
        REPORT.add(new String[] {"Unauthorized", "student3 asks about student1", moodleResult + " [Moodle rule]",
                newResult, same ? "SAME" : "DIFFERENT"});
        if (!same) {
            fail("Moodle denies student3 the completion of student1, but the new system answered HTTP "
                    + response.status() + " " + response.body());
        }
    }

    private void compareDecision(String scenario, String student, String note) {
        JsonNode moodleStatus = moodle.status(PREREQUISITE, student);
        if (moodleStatus.has("error")) {
            fail("Moodle returned error '" + moodleStatus.path("error").asText() + "' for " + PREREQUISITE + "/" + student);
        }
        boolean completed = moodleStatus.path("completed").asBoolean();

        ObjectNode expected = MAPPER.createObjectNode();
        expected.put("student", student);
        expected.put("course", COURSE);
        expected.put("allowed", completed);
        var missing = expected.putArray("missingPrerequisites");
        if (!completed) {
            missing.add(PREREQUISITE);
        }

        FigBackendClient.Response response = backend.get(ENDPOINT, Map.of("student", student, "course", COURSE), student);
        String newResult;
        List<String> differences;
        if (response.status() == 200 && response.body().isObject()) {
            ObjectNode actual = ((ObjectNode) response.body()).deepCopy();
            String reason = actual.path("reason").asText();
            actual.remove("reason"); // the new system's explanation; Moodle has no such field
            newResult = "allowed=" + actual.path("allowed").asBoolean() + " (" + reason + ")";
            differences = JsonDiff.diff(expected, actual);
        } else {
            newResult = "HTTP " + response.status();
            differences = List.of("$: expected HTTP 200 with a decision but was HTTP " + response.status() + " " + response.body());
        }

        REPORT.add(new String[] {scenario, student + " -> " + COURSE + " (" + note + ")",
                PREREQUISITE + " completed=" + completed + " [" + moodle.source() + "]", newResult,
                differences.isEmpty() ? "SAME" : "DIFFERENT"});
        if (!differences.isEmpty()) {
            StringBuilder message = new StringBuilder("New system differs from Moodle for ").append(student)
                    .append(" -> ").append(COURSE).append(" in ").append(differences.size()).append(" place(s):");
            differences.forEach(line -> message.append("\n  - ").append(line));
            fail(message.toString());
        }
    }

    /** Prints the comparison table (for the presentation) and writes it to target/golden-master/prerequisite.md. */
    @AfterAll
    static void report() {
        if (REPORT.isEmpty()) {
            return;
        }
        long same = REPORT.stream().filter(r -> r[4].equals("SAME")).count();
        String[] header = {"Scenario", "Input", "Moodle 5.3.1", "New system", "Result"};
        int[] width = new int[header.length];
        for (String[] row : concat(header)) {
            for (int i = 0; i < row.length; i++) {
                width[i] = Math.max(width[i], row[i].length());
            }
        }
        StringBuilder text = new StringBuilder("\nGolden master #13: prerequisite check (CS201 requires CS101)\n")
                .append("Moodle side: ").append(describe(moodle)).append(" | New system: ").append(backend.baseUrl())
                .append("\n\n");
        StringBuilder md = new StringBuilder("## Golden master #13: prerequisite check (CS201 requires CS101)\n\n")
                .append("Moodle side: ").append(describe(moodle)).append(". New system: `GET ").append(ENDPOINT).append("`.\n\n")
                .append("| ").append(String.join(" | ", header)).append(" |\n|---|---|---|---|---|\n");
        for (String[] row : concat(header)) {
            for (int i = 0; i < row.length; i++) {
                text.append(String.format("%-" + width[i] + "s", row[i])).append(i < row.length - 1 ? "  " : "\n");
            }
        }
        REPORT.forEach(row -> md.append("| ").append(String.join(" | ", row)).append(" |\n"));
        String summary = same + "/" + REPORT.size() + " scenarios give the same result in Moodle and the new system.";
        text.append("\n").append(summary).append("\n")
                .append("Intentional difference (REQ-ADV-08): Moodle still lets student3 enrol in CS201; the new system says no.\n");
        md.append("\n**").append(summary).append("**\n\n")
                .append("Intentional difference (REQ-ADV-08): Moodle still lets student3 enrol in CS201; the new system says no.\n");
        System.out.println(text);
        try {
            Path out = Path.of("target", "golden-master", "prerequisite.md");
            Files.createDirectories(out.getParent());
            Files.writeString(out, md, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String describe(MoodleCompletion moodle) {
        return moodle.source().equals("live") ? "live Moodle (REST web service)" : "saved Moodle snapshots (tests/golden-master/snapshots)";
    }

    private static List<String[]> concat(String[] header) {
        List<String[]> rows = new ArrayList<>();
        rows.add(header);
        rows.addAll(REPORT);
        return rows;
    }
}
