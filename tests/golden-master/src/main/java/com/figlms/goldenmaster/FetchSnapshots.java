package com.figlms.goldenmaster;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Locale;
import java.util.Map;

/**
 * One command that reads the sample data from the running Moodle and writes the snapshots:
 * {@code ./mvnw compile exec:java} (run in tests/golden-master).
 * <ul>
 *   <li>{@code courses.json}: core_course_get_courses</li>
 *   <li>{@code enrolled-<course>.json}: core_enrol_get_enrolled_users, per course</li>
 *   <li>{@code completion-<course>-<user>.json}: core_completion_get_course_completion_status, per enrolled student</li>
 * </ul>
 */
public final class FetchSnapshots {

    private FetchSnapshots() {
    }

    public static void main(String[] args) {
        MoodleClient moodle = MoodleClient.fromEnvironment();
        Snapshots snapshots = Snapshots.defaultLocation();

        JsonNode rawCourses = moodle.call("core_course_get_courses");
        System.out.println("saved " + snapshots.save("courses", MoodleExpected.courses(rawCourses)));

        for (JsonNode course : rawCourses) {
            int courseId = course.path("id").asInt();
            if (courseId == 1) {
                continue; // the site course
            }
            String shortname = course.path("shortname").asText();
            JsonNode users = moodle.call("core_enrol_get_enrolled_users", Map.of("courseid", String.valueOf(courseId)));
            String courseKey = shortname.toLowerCase(Locale.ROOT);
            System.out.println("saved " + snapshots.save("enrolled-" + courseKey,
                    MoodleExpected.enrolledUsers(shortname, users)));

            for (JsonNode user : users) {
                boolean student = false;
                for (JsonNode role : user.path("roles")) {
                    student |= "student".equals(role.path("shortname").asText());
                }
                if (!student) {
                    continue;
                }
                String username = user.path("username").asText();
                String name = "completion-" + courseKey + "-" + username;
                JsonNode expected;
                try {
                    JsonNode raw = moodle.call("core_completion_get_course_completion_status",
                            Map.of("courseid", String.valueOf(courseId), "userid", user.path("id").asText()));
                    expected = MoodleExpected.completionStatus(shortname, username, raw);
                } catch (MoodleClient.MoodleException e) {
                    expected = MoodleExpected.error(shortname, username, e.errorCode());
                }
                System.out.println("saved " + snapshots.save(name, expected));
            }
        }
    }
}
