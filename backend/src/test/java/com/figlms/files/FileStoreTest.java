package com.figlms.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.figlms.ApiException;
import com.figlms.files.api.FileStore;
import com.figlms.files.api.StoredFile;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * REQ-FIL-01..03, 06 on the real store. Not transactional, so content removal after commit runs; the rows are
 * removed after each test.
 */
@SpringBootTest(properties = "spring.profiles.active=test")
class FileStoreTest {

    static final String COMPONENT = "filestoretest";

    @Autowired
    FileStore store;

    @Autowired
    JdbcClient jdbc;

    @Value("${fig.files.root}")
    Path root;

    long user() {
        return jdbc.sql("SELECT id FROM idn_user WHERE username = 'student1'").query(Long.class).single();
    }

    StoredFile put(long item, String name, String content) {
        return store.store(COMPONENT, "area", item, name, "text/plain",
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), user());
    }

    Path contentPath(StoredFile f) {
        String h = f.contentHash();
        return root.toAbsolutePath().normalize().resolve(h.substring(0, 2)).resolve(h.substring(2, 4)).resolve(h);
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM fil_file WHERE component = ?").param(COMPONENT).update();
    }

    @Test
    void normal_sameContentIsStoredOnce() throws Exception {
        StoredFile a = put(1, "a.txt", "same bytes");
        StoredFile b = put(2, "b.txt", "same bytes");
        assertThat(a.contentHash()).isEqualTo(b.contentHash()).hasSize(64);
        assertThat(contentPath(a)).exists();
        try (InputStream in = store.open(b)) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("same bytes");
        }
        assertThat(a.size()).isEqualTo(10);
    }

    @Test
    void normal_contentIsRemovedOnlyWhenNoFileUsesIt() {
        StoredFile a = put(1, "a.txt", "shared content");
        StoredFile b = put(2, "b.txt", "shared content");
        store.delete(a.id());
        assertThat(contentPath(b)).exists();
        store.delete(b.id());
        assertThat(contentPath(b)).doesNotExist();
    }

    @Test
    void normal_sameNameInTheSameAreaReplacesTheFile() {
        put(1, "a.txt", "v1");
        StoredFile v2 = put(1, "a.txt", "v2");
        assertThat(store.list(COMPONENT, "area", 1)).extracting(StoredFile::id).containsExactly(v2.id());
    }

    @Test
    void normal_copyAreaSharesTheContent() {
        StoredFile a = put(1, "a.txt", "copied");
        store.copyArea(COMPONENT, "area", 1, 9);
        assertThat(store.list(COMPONENT, "area", 9)).singleElement()
                .satisfies(f -> assertThat(f.contentHash()).isEqualTo(a.contentHash()));
    }

    @Test
    void invalid_nameThatCleansToNothing() {
        assertThatThrownBy(() -> put(1, "..", "x")).isInstanceOf(ApiException.class).hasMessage("Invalid file name");
    }

    @Test
    void boundary_uploadLimitIsTheSmallestPositiveLimit() {
        long site = 20L * 1024 * 1024;
        assertThat(store.maxUploadBytes(0, 0)).isEqualTo(site);
        assertThat(store.maxUploadBytes(5_000, 0)).isEqualTo(5_000);
        assertThat(store.maxUploadBytes(5_000, 1_000)).isEqualTo(1_000);
        assertThat(store.maxUploadBytes(0, site + 1)).isEqualTo(site);
    }

    @Test
    void boundary_noTemporaryFilesAreLeftBehind() throws Exception {
        put(1, "a.txt", "temp check");
        try (var files = Files.list(root.toAbsolutePath().normalize())) {
            assertThat(files.filter(p -> p.getFileName().toString().endsWith(".tmp"))).isEmpty();
        }
    }
}
