package com.figlms.goldenmaster;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

/** Compares two JSON trees and lists every difference with its path, e.g. {@code $.users[1].roles[0]}. */
public final class JsonDiff {

    private JsonDiff() {
    }

    /** @return one readable line per difference; empty when the trees are equal */
    public static List<String> diff(JsonNode expected, JsonNode actual) {
        List<String> out = new ArrayList<>();
        compare("$", expected, actual, out);
        return out;
    }

    private static void compare(String path, JsonNode expected, JsonNode actual, List<String> out) {
        if (expected.isObject() && actual.isObject()) {
            TreeSet<String> names = new TreeSet<>();
            expected.fieldNames().forEachRemaining(names::add);
            actual.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                String child = path + "." + name;
                if (!actual.has(name)) {
                    out.add(child + ": missing in actual (expected " + show(expected.get(name)) + ")");
                } else if (!expected.has(name)) {
                    out.add(child + ": unexpected in actual (value " + show(actual.get(name)) + ")");
                } else {
                    compare(child, expected.get(name), actual.get(name), out);
                }
            }
        } else if (expected.isArray() && actual.isArray()) {
            if (expected.size() != actual.size()) {
                out.add(path + ": expected " + expected.size() + " item(s) but was " + actual.size());
            }
            int common = Math.min(expected.size(), actual.size());
            for (int i = 0; i < common; i++) {
                compare(path + "[" + i + "]", expected.get(i), actual.get(i), out);
            }
        } else if (expected.isNumber() && actual.isNumber()) {
            if (Math.abs(expected.doubleValue() - actual.doubleValue()) > 1e-9) {
                out.add(path + ": expected " + show(expected) + " but was " + show(actual));
            }
        } else if (!expected.equals(actual)) {
            out.add(path + ": expected " + show(expected) + " but was " + show(actual));
        }
    }

    private static String show(JsonNode node) {
        String text = node.toString();
        return text.length() > 120 ? text.substring(0, 117) + "..." : text;
    }
}
