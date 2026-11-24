package com.figlms.goldenmaster.advising;

import com.fasterxml.jackson.databind.JsonNode;
import com.figlms.goldenmaster.MoodleClient;
import com.figlms.goldenmaster.MoodleExpected;
import com.figlms.goldenmaster.Snapshots;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The Moodle side of a golden master test: course completion of one student, as {@link MoodleExpected#completionStatus}
 * shapes it. Read live from Moodle when it runs and {@code MOODLE_WS_TOKEN} is set, otherwise from the snapshots.
 * {@code -Dgolden.moodle=snapshots} forces the snapshots.
 */
abstract class MoodleCompletion {

    abstract JsonNode status(String course, String username);

    /** "live Moodle at ..." or "snapshots in ...", printed in the report. */
    abstract String source();

    static MoodleCompletion liveOrSnapshots() {
        Snapshots snapshots = Snapshots.defaultLocation();
        if (!"snapshots".equals(System.getProperty("golden.moodle"))) {
            try {
                MoodleClient client = MoodleClient.fromEnvironment();
                return new Live(client, client.call("core_course_get_courses"));
            } catch (RuntimeException e) {
                System.out.println("Moodle is not available (" + e.getMessage() + "), using the saved snapshots.");
            }
        }
        return new FromSnapshots(snapshots);
    }

    static final class FromSnapshots extends MoodleCompletion {
        private final Snapshots snapshots;

        FromSnapshots(Snapshots snapshots) {
            this.snapshots = snapshots;
        }

        @Override
        JsonNode status(String course, String username) {
            String name = "completion-" + course.toLowerCase(Locale.ROOT) + "-" + username;
            if (!snapshots.exists(name)) {
                throw new AssertionError("No golden master snapshot '" + name + "' at " + snapshots.fileFor(name)
                        + ". Fetch it from Moodle first (see tests/golden-master/README.md).");
            }
            return snapshots.load(name);
        }

        @Override
        String source() {
            return "snapshot";
        }
    }

    static final class Live extends MoodleCompletion {
        private final MoodleClient client;
        private final JsonNode courses;
        private final Map<String, Map<String, String>> userIds = new HashMap<>();

        Live(MoodleClient client, JsonNode courses) {
            this.client = client;
            this.courses = courses;
        }

        @Override
        JsonNode status(String course, String username) {
            String courseId = courseId(course);
            String userId = userIds.computeIfAbsent(courseId, this::enrolledUserIds).get(username);
            if (userId == null) {
                throw new AssertionError(username + " is not enrolled in " + course + " in Moodle. Load the sample data "
                        + "(infra/README.md).");
            }
            try {
                JsonNode raw = client.call("core_completion_get_course_completion_status",
                        Map.of("courseid", courseId, "userid", userId));
                return MoodleExpected.completionStatus(course, username, raw);
            } catch (MoodleClient.MoodleException e) {
                return MoodleExpected.error(course, username, e.errorCode());
            }
        }

        @Override
        String source() {
            return "live";
        }

        private String courseId(String shortname) {
            for (JsonNode c : courses) {
                if (shortname.equals(c.path("shortname").asText())) {
                    return c.path("id").asText();
                }
            }
            throw new AssertionError("Course " + shortname + " not found in Moodle. Load the sample data (infra/README.md).");
        }

        private Map<String, String> enrolledUserIds(String courseId) {
            Map<String, String> ids = new HashMap<>();
            for (JsonNode user : client.call("core_enrol_get_enrolled_users", Map.of("courseid", courseId))) {
                ids.put(user.path("username").asText(), user.path("id").asText());
            }
            return ids;
        }
    }
}
