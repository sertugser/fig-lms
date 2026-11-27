package com.figlms.files.internal;

import com.figlms.ApiException;
import com.figlms.files.api.FileAccessPolicy;
import com.figlms.files.api.FileStore;
import com.figlms.files.api.StoredFile;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/files/{id}}: download a file (REQ-FIL-05, Moodle {@code pluginfile.php}).
 *
 * <p>The owning component's {@link FileAccessPolicy} decides. A missing file and a refused one give the same 404, as
 * Moodle's {@code send_file_not_found()}, so the answer does not tell whether the file exists. Files are always sent
 * as attachments (Moodle forces the download of submission files "for security").
 */
@RestController
@RequestMapping("/api/files")
@EnableConfigurationProperties(FilesProperties.class)
class FileController {

    private final FileStore store;
    private final Map<String, FileAccessPolicy> policies;

    FileController(FileStore store, List<FileAccessPolicy> policies) {
        this.store = store;
        this.policies = policies.stream()
                .collect(Collectors.toUnmodifiableMap(FileAccessPolicy::component, Function.identity()));
    }

    @GetMapping("/{id}")
    ResponseEntity<InputStreamResource> download(@PathVariable long id, Authentication caller) {
        StoredFile file = store.find(id).filter(f -> mayRead(f, caller.getName()))
                .orElseThrow(() -> ApiException.notFound("FILE_NOT_FOUND", "Sorry, the requested file could not be found"));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename(), java.nio.charset.StandardCharsets.UTF_8)
                                .build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(mediaType(file.mimetype()))
                .contentLength(file.size())
                .body(new InputStreamResource(store.open(file)));
    }

    private boolean mayRead(StoredFile file, String username) {
        FileAccessPolicy policy = policies.get(file.component());
        return policy != null && policy.canRead(file, username);
    }

    private static MediaType mediaType(String mimetype) {
        try {
            return MediaType.parseMediaType(mimetype);
        } catch (IllegalArgumentException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
