package com.figlms.files.api;

import java.util.regex.Pattern;

/** File name rules shared by every module that takes uploads. */
public final class FileNames {

    // Same character class as Moodle clean_param_value_file(): control characters and & < > " ` | ' : \ /
    private static final Pattern UNSAFE = Pattern.compile("[\\p{Cntrl}&<>\"`|':\\\\/]");

    private FileNames() {
    }

    /**
     * Moodle {@code PARAM_FILE} (REQ-FIL-04): removes the unsafe characters; {@code .} and {@code ..} become empty.
     * The result can be empty, which callers must reject.
     */
    public static String clean(String filename) {
        if (filename == null) {
            return "";
        }
        String cleaned = UNSAFE.matcher(filename).replaceAll("");
        return cleaned.equals(".") || cleaned.equals("..") ? "" : cleaned;
    }

    /**
     * Does the name end in one of the accepted extensions (REQ-ASG-05 accepted file types)? An empty list accepts
     * every type. Extensions are compared without case and may be given with or without the dot.
     */
    public static boolean hasAcceptedType(String filename, java.util.Collection<String> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            return true;
        }
        String lower = filename.toLowerCase(java.util.Locale.ROOT);
        for (String ext : extensions) {
            String e = ext.trim().toLowerCase(java.util.Locale.ROOT);
            if (e.isEmpty()) {
                continue;
            }
            if (!e.startsWith(".")) {
                e = "." + e;
            }
            if (lower.endsWith(e) && lower.length() > e.length()) {
                return true;
            }
        }
        return false;
    }
}
