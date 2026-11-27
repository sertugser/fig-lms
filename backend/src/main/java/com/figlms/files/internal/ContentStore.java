package com.figlms.files.internal;

import com.figlms.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * File contents on disk, named by their SHA-256 hash (REQ-FIL-01). Like Moodle {@code file_system_filedir}, a hash
 * {@code abcd...} is stored at {@code ab/cd/abcd...}, so equal bytes are stored once.
 *
 * <p>Moodle uses SHA-1; SHA-256 is used here because SHA-1 collisions can be made on purpose.
 */
@Component
class ContentStore {

    record Written(String hash, long size) {
    }

    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

    private final Path root;

    ContentStore(FilesProperties properties) {
        this.root = properties.root().toAbsolutePath().normalize();
    }

    /** Streams the content to disk and returns its hash; nothing is written twice. */
    Written write(InputStream content) {
        try {
            Files.createDirectories(root);
            Path temp = Files.createTempFile(root, "upload-", ".tmp");
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long size;
                try (OutputStream out = new DigestOutputStream(Files.newOutputStream(temp), digest)) {
                    size = content.transferTo(out);
                }
                String hash = HexFormat.of().formatHex(digest.digest());
                Path target = path(hash);
                if (!Files.exists(target)) {
                    Files.createDirectories(target.getParent());
                    try {
                        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
                    } catch (FileAlreadyExistsException parallelUpload) {
                        // Another upload stored the same bytes first.
                    }
                }
                return new Written(hash, size);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    InputStream read(String hash) {
        try {
            return Files.newInputStream(path(hash));
        } catch (IOException e) {
            throw ApiException.notFound("FILE_NOT_FOUND", "Sorry, the requested file could not be found");
        }
    }

    void remove(String hash) {
        try {
            Files.deleteIfExists(path(hash));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    Path path(String hash) {
        if (!HASH.matcher(hash).matches()) {
            throw new IllegalArgumentException("not a SHA-256 hash: " + hash);
        }
        return root.resolve(hash.substring(0, 2)).resolve(hash.substring(2, 4)).resolve(hash);
    }
}
