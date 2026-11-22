package com.figlms.goldenmaster;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The converters from raw Moodle responses to expected-result JSON, tested with fake Moodle data. */
class MoodleExpectedTest {

    @Test
    void coursesDropSiteCourseAndVolatileFieldsAndSortByShortname() {
        JsonNode result = MoodleExpected.courses(FakeData.read("courses-raw.json"));
        assertEquals(2, result.get("courses").size());
        assertEquals("CS101", result.at("/courses/0/shortname").asText());
        assertEquals("CS201", result.at("/courses/1/shortname").asText());
        assertFalse(result.at("/courses/0").has("id"));
        assertFalse(result.at("/courses/0").has("timemodified"));
        assertTrue(result.at("/courses/0/visible").asBoolean());
    }

    @Test
    void enrolledUsersAreSortedAndKeepRoleShortnamesOnly() {
        JsonNode result = MoodleExpected.enrolledUsers("CS101", FakeData.read("enrolled-raw.json"));
        assertEquals("CS101", result.get("course").asText());
        assertEquals("student1", result.at("/users/0/username").asText());
        assertEquals("[\"editingteacher\",\"student\"]", result.at("/users/1/roles").toString());
        assertFalse(result.at("/users/0").has("lastaccess"));
        assertFalse(result.at("/users/0").has("id"));
    }

    @Test
    void completionStatusKeepsCriteriaWithoutHtmlOrIds() {
        JsonNode result = MoodleExpected.completionStatus("CS201", "student1", FakeData.read("completion-raw.json"));
        assertFalse(result.get("completed").asBoolean());
        assertEquals("Introduction to Programming", result.at("/criteria/0/criteria").asText());
        assertTrue(result.at("/criteria/0/complete").asBoolean());
        assertEquals("Marked complete by teacher", result.at("/criteria/1/type").asText());
    }

    @Test
    void moodleErrorIsStoredAsResult() {
        JsonNode result = MoodleExpected.error("CS101", "student1", "invalidparameter");
        assertEquals("invalidparameter", result.get("error").asText());
    }
}
