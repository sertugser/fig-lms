package com.figlms.goldenmaster;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The comparison helper, tested with fake data: one match and one mismatch (plus a missing snapshot). */
class GoldenAssertTest {

    @TempDir
    Path dir;

    Snapshots snapshots;

    @BeforeEach
    void saveGoldenMaster() {
        snapshots = new Snapshots(dir);
        snapshots.save("prerequisite-student1", FakeData.json("""
                {"allowed": true, "reasons": [], "course": "CS201"}"""));
    }

    @Test
    void matchingResponsePasses() {
        // Key order and whitespace do not matter; numbers are compared by value.
        var actual = FakeData.json("""
                {"course": "CS201", "reasons": [], "allowed": true}""");
        assertDoesNotThrow(() -> GoldenAssert.assertMatchesGolden(snapshots, "prerequisite-student1", actual));
    }

    @Test
    void differentResponseFailsWithClearDifferences() {
        var actual = FakeData.json("""
                {"allowed": false, "reasons": ["CS101 not completed"], "course": "CS201", "extra": 1}""");
        AssertionError error = assertThrows(AssertionError.class,
                () -> GoldenAssert.assertMatchesGolden(snapshots, "prerequisite-student1", actual));
        String message = error.getMessage();
        assertTrue(message.contains("'prerequisite-student1' in 3 place(s)"), message);
        assertTrue(message.contains("$.allowed: expected true but was false"), message);
        assertTrue(message.contains("$.reasons: expected 0 item(s) but was 1"), message);
        assertTrue(message.contains("$.extra: unexpected in actual (value 1)"), message);
    }

    @Test
    void missingSnapshotTellsHowToFetchIt() {
        AssertionError error = assertThrows(AssertionError.class,
                () -> GoldenAssert.assertMatchesGolden(snapshots, "nope", FakeData.json("{}")));
        assertTrue(error.getMessage().contains("README.md"), error.getMessage());
    }
}
