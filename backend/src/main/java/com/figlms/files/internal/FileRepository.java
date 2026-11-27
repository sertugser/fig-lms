package com.figlms.files.internal;

import static com.figlms.Sql.instant;
import static com.figlms.Sql.ts;

import com.figlms.files.api.StoredFile;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** The {@code fil_file} table (V7). */
@Repository
class FileRepository {

    private static final String COLUMNS =
            "id, component, area, item_id, filename, mimetype, size_bytes, content_hash, user_id, time_created";

    private final JdbcClient jdbc;

    FileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    long insert(String component, String area, long itemId, String filename, String mimetype, long size, String hash,
            long userId, Instant now) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO fil_file (component, area, item_id, filename, mimetype, size_bytes, content_hash, user_id, time_created)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(component, area, itemId, filename, mimetype, size, hash, userId, ts(now))
                .update(keys, "id");
        return keys.getKey().longValue();
    }

    Optional<StoredFile> find(long id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM fil_file WHERE id = ?").param(id).query(FileRepository::row)
                .optional();
    }

    Optional<StoredFile> find(String component, String area, long itemId, String filename) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM fil_file WHERE component = ? AND area = ? AND item_id = ? AND filename = ?")
                .params(component, area, itemId, filename).query(FileRepository::row).optional();
    }

    List<StoredFile> list(String component, String area, long itemId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM fil_file WHERE component = ? AND area = ? AND item_id = ? ORDER BY filename")
                .params(component, area, itemId).query(FileRepository::row).list();
    }

    void delete(long id) {
        jdbc.sql("DELETE FROM fil_file WHERE id = ?").param(id).update();
    }

    boolean contentUsed(String hash) {
        return jdbc.sql("SELECT COUNT(*) FROM fil_file WHERE content_hash = ?").param(hash).query(Long.class).single() > 0;
    }

    private static StoredFile row(ResultSet rs, int n) throws SQLException {
        return new StoredFile(rs.getLong("id"), rs.getString("component"), rs.getString("area"), rs.getLong("item_id"),
                rs.getString("filename"), rs.getString("mimetype"), rs.getLong("size_bytes"),
                rs.getString("content_hash"), rs.getLong("user_id"), instant(rs, "time_created"));
    }
}
