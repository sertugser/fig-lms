package com.figlms.goldenmaster;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads and writes the "expected result" JSON files (snapshots) of Moodle. */
public final class Snapshots {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ObjectMapper SORTED = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path dir;

    public Snapshots(Path dir) {
        this.dir = dir;
    }

    /** {@code snapshots/} next to the pom, or the folder in system property {@code golden.snapshots}. */
    public static Snapshots defaultLocation() {
        return new Snapshots(Path.of(System.getProperty("golden.snapshots", "snapshots")));
    }

    public Path fileFor(String name) {
        return dir.resolve(name + ".json");
    }

    public boolean exists(String name) {
        return Files.exists(fileFor(name));
    }

    public JsonNode load(String name) {
        try {
            return MAPPER.readTree(Files.readString(fileFor(name), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read snapshot " + fileFor(name), e);
        }
    }

    /** Writes the snapshot with sorted keys and a trailing newline, so re-fetching gives no spurious git diff. */
    public Path save(String name, JsonNode content) {
        try {
            Files.createDirectories(dir);
            Object plain = SORTED.treeToValue(content, Object.class);
            String text = SORTED.writeValueAsString(plain).replace("\r\n", "\n") + "\n";
            return Files.writeString(fileFor(name), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write snapshot " + fileFor(name), e);
        }
    }
}
