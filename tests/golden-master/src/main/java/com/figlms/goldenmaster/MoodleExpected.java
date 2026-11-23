package com.figlms.goldenmaster;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * Turns raw Moodle responses into simple, stable "expected result" JSON.
 * Database ids, timestamps of last access and HTML are dropped, so a snapshot is the same on every Moodle install
 * and the new system can produce the same shape. Courses are identified by {@code shortname}, users by {@code username}.
 */
public final class MoodleExpected {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MoodleExpected() {
    }

    /** core_course_get_courses -> {"courses":[...]} without the site course (id 1), sorted by shortname. */
    public static JsonNode courses(JsonNode raw) {
        List<ObjectNode> list = new ArrayList<>();
        for (JsonNode course : raw) {
            if (course.path("id").asInt() == 1) {
                continue;
            }
            ObjectNode out = MAPPER.createObjectNode();
            out.put("shortname", course.path("shortname").asText());
            out.put("fullname", course.path("fullname").asText());
            out.put("idnumber", course.path("idnumber").asText());
            out.put("format", course.path("format").asText());
            out.put("startdate", course.path("startdate").asLong());
            out.put("enddate", course.path("enddate").asLong());
            out.put("visible", course.path("visible").asInt() == 1);
            list.add(out);
        }
        list.sort(Comparator.comparing(n -> n.get("shortname").asText()));
        ObjectNode result = MAPPER.createObjectNode();
        result.set("courses", MAPPER.createArrayNode().addAll(list));
        return result;
    }

    /** core_enrol_get_enrolled_users -> {"course":..., "users":[{username, fullname, roles}]}, sorted by username. */
    public static JsonNode enrolledUsers(String courseShortname, JsonNode raw) {
        List<ObjectNode> list = new ArrayList<>();
        for (JsonNode user : raw) {
            ObjectNode out = MAPPER.createObjectNode();
            out.put("username", user.path("username").asText());
            out.put("fullname", user.path("fullname").asText());
            TreeSet<String> roles = new TreeSet<>();
            user.path("roles").forEach(role -> roles.add(role.path("shortname").asText()));
            ArrayNode roleArray = out.putArray("roles");
            roles.forEach(roleArray::add);
            list.add(out);
        }
        list.sort(Comparator.comparing(n -> n.get("username").asText()));
        ObjectNode result = MAPPER.createObjectNode();
        result.put("course", courseShortname);
        result.set("users", MAPPER.createArrayNode().addAll(list));
        return result;
    }

    /** core_completion_get_course_completion_status -> overall result and each criterion (type, title, complete). */
    public static JsonNode completionStatus(String courseShortname, String username, JsonNode raw) {
        JsonNode status = raw.path("completionstatus");
        ObjectNode result = MAPPER.createObjectNode();
        result.put("course", courseShortname);
        result.put("username", username);
        result.put("completed", status.path("completed").asBoolean());
        ArrayNode criteria = result.putArray("criteria");
        for (JsonNode c : status.path("completions")) {
            ObjectNode out = criteria.addObject();
            out.put("type", c.path("details").path("type").asText());
            out.put("criteria", stripHtml(c.path("details").path("criteria").asText()));
            out.put("complete", c.path("complete").asBoolean());
        }
        return result;
    }

    /** A Moodle error is a valid golden result too (e.g. completion not enabled for the course). */
    public static JsonNode error(String courseShortname, String username, String errorCode) {
        ObjectNode result = MAPPER.createObjectNode();
        result.put("course", courseShortname);
        result.put("username", username);
        result.put("error", errorCode);
        return result;
    }

    static String stripHtml(String html) {
        return html.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
    }
}
