package com.figlms.goldenmaster;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** The shared comparison helper of all golden master tests. */
public final class GoldenAssert {

    private GoldenAssert() {
    }

    /** Compares {@code actual} (response of the new system) with snapshot {@code snapshotName} from the default folder. */
    public static void assertMatchesGolden(String snapshotName, JsonNode actual) {
        assertMatchesGolden(Snapshots.defaultLocation(), snapshotName, actual);
    }

    /**
     * @throws AssertionError listing every difference (path, expected, actual), or when the snapshot is missing
     */
    public static void assertMatchesGolden(Snapshots snapshots, String snapshotName, JsonNode actual) {
        if (!snapshots.exists(snapshotName)) {
            throw new AssertionError("No golden master snapshot '" + snapshotName + "' at "
                    + snapshots.fileFor(snapshotName) + ". Fetch it from Moodle first (see tests/golden-master/README.md).");
        }
        List<String> differences = JsonDiff.diff(snapshots.load(snapshotName), actual);
        if (!differences.isEmpty()) {
            StringBuilder message = new StringBuilder("New system differs from Moodle golden master '")
                    .append(snapshotName).append("' in ").append(differences.size()).append(" place(s):");
            differences.forEach(line -> message.append("\n  - ").append(line));
            throw new AssertionError(message.toString());
        }
    }
}
