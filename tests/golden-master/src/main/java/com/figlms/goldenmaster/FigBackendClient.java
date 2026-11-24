package com.figlms.goldenmaster;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Minimal client for the new system (the backend in {@code backend/}).
 * Settings come from the environment, or from {@code .env} / {@code infra/.env}: {@code FIG_BACKEND_URL}
 * (default http://localhost:8080) and {@code SAMPLE_USER_PASSWORD}, the shared password of the sample users.
 */
public class FigBackendClient {

    /** Status code and JSON body of one response ({@link NullNode} when the body is empty or not JSON). */
    public record Response(int status, JsonNode body) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String password;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public FigBackendClient(String baseUrl, String password) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.password = password;
    }

    public static FigBackendClient fromEnvironment() {
        Map<String, String> files = readEnvFiles();
        return new FigBackendClient(setting("FIG_BACKEND_URL", files, "http://localhost:8080"),
                setting("SAMPLE_USER_PASSWORD", files, ""));
    }

    public String baseUrl() {
        return baseUrl;
    }

    /** @return true if {@code /actuator/health} answers 200 */
    public boolean isUp() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/actuator/health"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (IOException | IllegalArgumentException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * GET {@code path} with query {@code params}, logged in as {@code username} (HTTP Basic, sample user password).
     * {@code username == null} sends no login.
     */
    public Response get(String path, Map<String, String> params, String username) {
        String query = params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path + (query.isEmpty() ? "" : "?" + query)))
                .timeout(Duration.ofSeconds(10)).GET();
        if (username != null) {
            if (password.isBlank()) {
                throw new IllegalStateException("SAMPLE_USER_PASSWORD is not set. Use the value the backend was started with "
                        + "(environment, .env or infra/.env).");
            }
            String credentials = Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
            request.header("Authorization", "Basic " + credentials);
        }
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode body;
            try {
                body = response.body().isBlank() ? NullNode.getInstance() : MAPPER.readTree(response.body());
            } catch (IOException notJson) {
                body = NullNode.getInstance();
            }
            return new Response(response.statusCode(), body);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot reach the new system at " + baseUrl + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling " + path, e);
        }
    }

    private static String setting(String key, Map<String, String> files, String fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = files.get(key);
        }
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    /** Reads {@code .env} and then {@code infra/.env} from the nearest folder (working directory or a parent) that has one. */
    private static Map<String, String> readEnvFiles() {
        Map<String, String> values = new LinkedHashMap<>();
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path root = dir.resolve(".env");
            Path infra = dir.resolve("infra").resolve(".env");
            if (Files.isRegularFile(root) || Files.isRegularFile(infra)) {
                for (Path file : new Path[] {root, infra}) {
                    if (Files.isRegularFile(file)) {
                        try {
                            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                                String trimmed = line.trim();
                                int eq = trimmed.indexOf('=');
                                if (!trimmed.startsWith("#") && eq > 0) {
                                    values.putIfAbsent(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
                                }
                            }
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    }
                }
                break;
            }
        }
        return values;
    }
}
