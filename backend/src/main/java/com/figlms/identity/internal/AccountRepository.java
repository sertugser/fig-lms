package com.figlms.identity.internal;

import com.figlms.identity.api.UserAccount;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The {@code idn_user} table. */
@Repository
class AccountRepository {

    /** Everything the login needs about one account. */
    record LoginRecord(long id, String username, String passwordHash, boolean suspended, boolean siteAdmin,
            int failedCount, Instant lastFailedAt, Instant lockedUntil) {
    }

    private static final String ACCOUNT_COLUMNS =
            "id, username, firstname, lastname, email, suspended, site_admin";

    private final JdbcClient jdbc;

    AccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<UserAccount> findByUsername(String username) {
        return jdbc.sql("SELECT " + ACCOUNT_COLUMNS + " FROM idn_user WHERE username = ? AND deleted = FALSE")
                .param(username).query(AccountRepository::account).optional();
    }

    Optional<UserAccount> findById(long id) {
        return jdbc.sql("SELECT " + ACCOUNT_COLUMNS + " FROM idn_user WHERE id = ? AND deleted = FALSE")
                .param(id).query(AccountRepository::account).optional();
    }

    Optional<LoginRecord> findLogin(String username) {
        return jdbc.sql("""
                        SELECT id, username, password_hash, suspended, site_admin, failed_count, last_failed_at, locked_until
                        FROM idn_user WHERE username = ? AND deleted = FALSE""")
                .param(username)
                .query((rs, n) -> new LoginRecord(rs.getLong("id"), rs.getString("username"),
                        rs.getString("password_hash"), rs.getBoolean("suspended"), rs.getBoolean("site_admin"),
                        rs.getInt("failed_count"), instant(rs.getObject("last_failed_at", OffsetDateTime.class)),
                        instant(rs.getObject("locked_until", OffsetDateTime.class))))
                .optional();
    }

    boolean usernameExists(String username) {
        return jdbc.sql("SELECT COUNT(*) FROM idn_user WHERE username = ?").param(username)
                .query(Integer.class).single() > 0;
    }

    long insert(String username, String passwordHash, String firstname, String lastname, String email, Instant now) {
        jdbc.sql("""
                        INSERT INTO idn_user (username, password_hash, firstname, lastname, email, created_at, password_changed_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)""")
                .params(username, passwordHash, firstname, lastname, email, offset(now), offset(now)).update();
        return jdbc.sql("SELECT id FROM idn_user WHERE username = ?").param(username).query(Long.class).single();
    }

    void updatePassword(long id, String passwordHash, Instant now) {
        jdbc.sql("UPDATE idn_user SET password_hash = ?, password_changed_at = ? WHERE id = ?")
                .params(passwordHash, offset(now), id).update();
    }

    void setSuspended(long id, boolean suspended) {
        jdbc.sql("UPDATE idn_user SET suspended = ? WHERE id = ?").params(suspended, id).update();
    }

    void recordFailure(long id, int failedCount, Instant at, Instant lockedUntil) {
        jdbc.sql("UPDATE idn_user SET failed_count = ?, last_failed_at = ?, locked_until = ? WHERE id = ?")
                .params(failedCount, offset(at), offset(lockedUntil), id).update();
    }

    void resetFailures(long id) {
        jdbc.sql("UPDATE idn_user SET failed_count = 0, last_failed_at = NULL, locked_until = NULL WHERE id = ?")
                .param(id).update();
    }

    List<String> sampleAccounts() {
        return jdbc.sql("SELECT username FROM idn_user WHERE sample_account = TRUE ORDER BY username")
                .query(String.class).list();
    }

    void setSamplePasswordHash(String passwordHash) {
        jdbc.sql("UPDATE idn_user SET password_hash = ? WHERE sample_account = TRUE").param(passwordHash).update();
    }

    private static UserAccount account(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new UserAccount(rs.getLong("id"), rs.getString("username"), rs.getString("firstname"),
                rs.getString("lastname"), rs.getString("email"), rs.getBoolean("suspended"),
                rs.getBoolean("site_admin"));
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime offset(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
