package com.figlms.goldenmaster;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonDiffTest {

    @Test
    void equalTreesHaveNoDifferences() {
        assertTrue(JsonDiff.diff(FakeData.json("{\"a\":[1,2.0,{\"b\":\"x\"}]}"),
                FakeData.json("{\"a\":[1.0,2,{\"b\":\"x\"}]}")).isEmpty());
    }

    @Test
    void reportsPathOfEveryDifference() {
        List<String> diff = JsonDiff.diff(
                FakeData.json("{\"users\":[{\"roles\":[\"student\"]},{\"roles\":[\"teacher\"]}],\"gone\":1}"),
                FakeData.json("{\"users\":[{\"roles\":[\"student\"]},{\"roles\":[\"manager\"]}]}"));
        assertEquals(List.of(
                "$.gone: missing in actual (expected 1)",
                "$.users[1].roles[0]: expected \"teacher\" but was \"manager\""), diff);
    }

    @Test
    void typeChangeIsADifference() {
        assertEquals(1, JsonDiff.diff(FakeData.json("{\"a\":\"1\"}"), FakeData.json("{\"a\":1}")).size());
    }
}
