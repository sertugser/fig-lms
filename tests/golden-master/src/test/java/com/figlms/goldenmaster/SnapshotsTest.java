package com.figlms.goldenmaster;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotsTest {

    @TempDir
    Path dir;

    @Test
    void saveSortsKeysAndRoundTrips() throws Exception {
        Snapshots snapshots = new Snapshots(dir);
        var content = FakeData.json("{\"b\":1,\"a\":{\"z\":true,\"c\":[1,2]}}");
        snapshots.save("sample", content);

        assertTrue(snapshots.exists("sample"));
        assertFalse(snapshots.exists("other"));
        assertEquals(content, snapshots.load("sample"));
        String text = Files.readString(snapshots.fileFor("sample"));
        assertTrue(text.indexOf("\"a\"") < text.indexOf("\"b\""), text);
        assertTrue(text.endsWith("}\n"), "file ends with a single newline");
    }
}
