package com.figlms.goldenmaster;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Minimal Moodle REST web service client.
 * Settings come from the environment, or from {@code infra/.env}: {@code MOODLE_URL} (default http://localhost:8000)
 * and {@code MOODLE_WS_TOKEN}. The token is sent in the POST body, never in the URL.
 */
public class MoodleClient {

    /** A Moodle web service error such as {@code invalidparameter}. */
    public static class MoodleException extends RuntimeException {
        private final String errorCode;

        public MoodleException(String errorCode, String message) {
            super(errorCode + ": " + message);
            this.errorCode = errorCode;
        }

        public String errorCode() {
            return errorCode;
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String token;
    private final HttpClient http = HttpClient.newHttpClient();

    public MoodleClient(String baseUrl, String token) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.token = token;
    }

    public static MoodleClient fromEnvironment() {
        Map<String, String> envFile = readEnvFile();
        String url = setting("MOODLE_URL", envFile, "http://localhost:8000");
        String token = setting("MOODLE_WS_TOKEN", envFile, "");
        if (token.isBlank()) {
            throw new IllegalStateException("MOODLE_WS_TOKEN is not set. Put it in infra/.env or the environment "
                    + "(see infra/README.md, section REST API token).");
        }
        return new MoodleClient(url, token);
    }

    /** Calls a web service function; {@code params} use Moodle's flat form, e.g. {@code courseid=2}, {@code ids[0]=2}. */
    public JsonNode call(String function, Map<String, String> params) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("wstoken", token);
        form.put("wsfunction", function);
        form.put("moodlewsrestformat", "json");
        form.putAll(params);
        String body = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/webservice/rest/server.php"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Moodle answered HTTP " + response.statusCode() + " for " + function);
            }
            JsonNode json = MAPPER.readTree(response.body());
            if (json.isObject() && json.has("exception")) {
                throw new MoodleException(json.path("errorcode").asText(), json.path("message").asText());
            }
            return json;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot reach Moodle at " + baseUrl + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling " + function, e);
        }
    }

    public JsonNode call(String function) {
        return call(function, Map.of());
    }

    private static String setting(String key, Map<String, String> envFile, String fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = envFile.get(key);
        }
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    /** Looks for infra/.env in the working directory and its parents. */
    private static Map<String, String> readEnvFile() {
        Map<String, String> values = new LinkedHashMap<>();
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path file = dir.resolve("infra").resolve(".env");
            if (Files.isRegularFile(file)) {
                try {
                    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                        String trimmed = line.trim();
                        int eq = trimmed.indexOf('=');
                        if (!trimmed.startsWith("#") && eq > 0) {
                            values.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
                        }
                    }
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
                break;
            }
        }
        return values;
    }
}
