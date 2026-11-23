package com.figlms.goldenmaster;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** Loads the fake Moodle responses under src/test/resources/fake. */
final class FakeData {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FakeData() {
    }

    static JsonNode read(String name) {
        try (InputStream in = FakeData.class.getResourceAsStream("/fake/" + name)) {
            return MAPPER.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static JsonNode json(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
