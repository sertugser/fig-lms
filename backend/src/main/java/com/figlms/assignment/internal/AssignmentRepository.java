package com.figlms.assignment.internal;

import static com.figlms.Sql.instant;
import static com.figlms.Sql.nullableDouble;
import static com.figlms.Sql.nullableLong;
import static com.figlms.Sql.ts;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** The {@code asg_*} tables (V8). */
@Repository
class AssignmentRepository {

    /** Settings of one assignment; {@code null} dates are disabled boundaries. */
    record AssignmentRow(long id, long courseId, String name, String intro, Instant allowSubmissionsFrom,
            Instant dueDate, Instant cutoffDate, boolean submissionDrafts, boolean requireStatement, double maxGrade,
            Double gradePass, ReopenMethod reopenMethod, int maxAttempts, boolean onlineTextEnabled,
            boolean fileEnabled, int maxFiles, long maxBytes, List<String> acceptedTypes, CompletionRule completion,
            String availability, Long gradeItemId) {

        String activityKey() {
            return "assignment:" + id;
        }

        boolean unlimitedAttempts() {
            return maxAttempts == -1;
        }
    }

    record OverrideRow(Instant allowSubmissionsFrom, Instant dueDate, Instant cutoffDate) {
    }

    record FlagRow(boolean locked, Instant extensionDueDate) {
        static final FlagRow NONE = new FlagRow(false, null);
    }

    record SubmissionRow(long id, long assignmentId, long userId, int attemptNumber, SubmissionStatus status,
            boolean latest, String onlineText, Instant timeCreated, Instant timeModified) {
    }

    record GradeRow(long userId, int attemptNumber, Double grade, Long graderId, Instant timeModified) {
    }

    private static final String ASSIGNMENT_COLUMNS = """
            id, course_id, name, intro, allow_submissions_from, due_date, cutoff_date, submission_drafts,
            require_statement, max_grade, grade_pass, reopen_method, max_attempts, online_text_enabled, file_enabled,
            max_files, max_bytes, accepted_types, completion, availability, grade_item_id""";

    private static final String SUBMISSION_COLUMNS =
            "id, assignment_id, user_id, attempt_number, status, latest, online_text, time_created, time_modified";

    private final JdbcClient jdbc;

    AssignmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---- assignments

    long insert(AssignmentRow a, Instant now) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO asg_assignment (course_id, name, intro, allow_submissions_from, due_date, cutoff_date,
                    submission_drafts, require_statement, max_grade, grade_pass, reopen_method, max_attempts,
                    online_text_enabled, file_enabled, max_files, max_bytes, accepted_types, completion, availability,
                    time_modified)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(a.courseId(), a.name(), a.intro(), ts(a.allowSubmissionsFrom()), ts(a.dueDate()),
                        ts(a.cutoffDate()), a.submissionDrafts(), a.requireStatement(), a.maxGrade(), a.gradePass(),
                        a.reopenMethod().name(), a.maxAttempts(), a.onlineTextEnabled(), a.fileEnabled(), a.maxFiles(),
                        a.maxBytes(), String.join(",", a.acceptedTypes()), a.completion().name(), a.availability(),
                        ts(now))
                .update(keys, "id");
        return keys.getKey().longValue();
    }

    void update(AssignmentRow a, Instant now) {
        jdbc.sql("""
                UPDATE asg_assignment SET name = ?, intro = ?, allow_submissions_from = ?, due_date = ?, cutoff_date = ?,
                    submission_drafts = ?, require_statement = ?, max_grade = ?, grade_pass = ?, reopen_method = ?,
                    max_attempts = ?, online_text_enabled = ?, file_enabled = ?, max_files = ?, max_bytes = ?,
                    accepted_types = ?, completion = ?, availability = ?, time_modified = ?
                WHERE id = ?""")
                .params(a.name(), a.intro(), ts(a.allowSubmissionsFrom()), ts(a.dueDate()), ts(a.cutoffDate()),
                        a.submissionDrafts(), a.requireStatement(), a.maxGrade(), a.gradePass(), a.reopenMethod().name(),
                        a.maxAttempts(), a.onlineTextEnabled(), a.fileEnabled(), a.maxFiles(), a.maxBytes(),
                        String.join(",", a.acceptedTypes()), a.completion().name(), a.availability(), ts(now), a.id())
                .update();
    }

    void setGradeItem(long assignmentId, long gradeItemId) {
        jdbc.sql("UPDATE asg_assignment SET grade_item_id = ? WHERE id = ?").params(gradeItemId, assignmentId).update();
    }

    Optional<AssignmentRow> find(long id) {
        return jdbc.sql("SELECT " + ASSIGNMENT_COLUMNS + " FROM asg_assignment WHERE id = ?").param(id)
                .query(AssignmentRepository::assignment).optional();
    }

    List<AssignmentRow> inCourse(long courseId) {
        return jdbc.sql("SELECT " + ASSIGNMENT_COLUMNS + " FROM asg_assignment WHERE course_id = ? ORDER BY id")
                .param(courseId).query(AssignmentRepository::assignment).list();
    }

    // ---- overrides and flags

    Optional<OverrideRow> override(long assignmentId, long userId) {
        return jdbc.sql("""
                SELECT allow_submissions_from, due_date, cutoff_date FROM asg_override
                WHERE assignment_id = ? AND user_id = ?""")
                .params(assignmentId, userId)
                .query((rs, n) -> new OverrideRow(instant(rs, "allow_submissions_from"), instant(rs, "due_date"),
                        instant(rs, "cutoff_date")))
                .optional();
    }

    void saveOverride(long assignmentId, long userId, OverrideRow o) {
        deleteOverride(assignmentId, userId);
        jdbc.sql("""
                INSERT INTO asg_override (assignment_id, user_id, allow_submissions_from, due_date, cutoff_date)
                VALUES (?, ?, ?, ?, ?)""")
                .params(assignmentId, userId, ts(o.allowSubmissionsFrom()), ts(o.dueDate()), ts(o.cutoffDate()))
                .update();
    }

    void deleteOverride(long assignmentId, long userId) {
        jdbc.sql("DELETE FROM asg_override WHERE assignment_id = ? AND user_id = ?").params(assignmentId, userId)
                .update();
    }

    FlagRow flags(long assignmentId, long userId) {
        return jdbc.sql("SELECT locked, extension_due_date FROM asg_user_flag WHERE assignment_id = ? AND user_id = ?")
                .params(assignmentId, userId)
                .query((rs, n) -> new FlagRow(rs.getBoolean("locked"), instant(rs, "extension_due_date")))
                .optional().orElse(FlagRow.NONE);
    }

    void saveFlags(long assignmentId, long userId, FlagRow flags) {
        int updated = jdbc.sql("UPDATE asg_user_flag SET locked = ?, extension_due_date = ? WHERE assignment_id = ? AND user_id = ?")
                .params(flags.locked(), ts(flags.extensionDueDate()), assignmentId, userId).update();
        if (updated == 0) {
            jdbc.sql("INSERT INTO asg_user_flag (assignment_id, user_id, locked, extension_due_date) VALUES (?, ?, ?, ?)")
                    .params(assignmentId, userId, flags.locked(), ts(flags.extensionDueDate())).update();
        }
    }

    // ---- submissions (one row per attempt; latest = the current attempt)

    Optional<SubmissionRow> latestSubmission(long assignmentId, long userId) {
        return jdbc.sql("SELECT " + SUBMISSION_COLUMNS + " FROM asg_submission WHERE assignment_id = ? AND user_id = ? AND latest = TRUE")
                .params(assignmentId, userId).query(AssignmentRepository::submission).optional();
    }

    Optional<SubmissionRow> submission(long id) {
        return jdbc.sql("SELECT " + SUBMISSION_COLUMNS + " FROM asg_submission WHERE id = ?").param(id)
                .query(AssignmentRepository::submission).optional();
    }

    Optional<SubmissionRow> attempt(long assignmentId, long userId, int attemptNumber) {
        return jdbc.sql("SELECT " + SUBMISSION_COLUMNS + " FROM asg_submission WHERE assignment_id = ? AND user_id = ? AND attempt_number = ?")
                .params(assignmentId, userId, attemptNumber).query(AssignmentRepository::submission).optional();
    }

    List<SubmissionRow> attempts(long assignmentId, long userId) {
        return jdbc.sql("SELECT " + SUBMISSION_COLUMNS + " FROM asg_submission WHERE assignment_id = ? AND user_id = ? ORDER BY attempt_number")
                .params(assignmentId, userId).query(AssignmentRepository::submission).list();
    }

    /** Adds an attempt and makes it the latest one. */
    long insertSubmission(long assignmentId, long userId, int attemptNumber, SubmissionStatus status, Instant now) {
        jdbc.sql("UPDATE asg_submission SET latest = FALSE WHERE assignment_id = ? AND user_id = ?")
                .params(assignmentId, userId).update();
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO asg_submission (assignment_id, user_id, attempt_number, status, latest, time_created, time_modified)
                VALUES (?, ?, ?, ?, TRUE, ?, ?)""")
                .params(assignmentId, userId, attemptNumber, status.name(), ts(now), ts(now))
                .update(keys, "id");
        return keys.getKey().longValue();
    }

    void updateSubmission(long id, SubmissionStatus status, String onlineText, Instant now) {
        jdbc.sql("UPDATE asg_submission SET status = ?, online_text = ?, time_modified = ? WHERE id = ?")
                .params(status.name(), onlineText, ts(now), id).update();
    }

    void setStatus(long id, SubmissionStatus status) {
        jdbc.sql("UPDATE asg_submission SET status = ? WHERE id = ?").params(status.name(), id).update();
    }

    // ---- grades (one row per attempt)

    Optional<GradeRow> grade(long assignmentId, long userId, int attemptNumber) {
        return jdbc.sql("""
                SELECT user_id, attempt_number, grade, grader_id, time_modified FROM asg_grade
                WHERE assignment_id = ? AND user_id = ? AND attempt_number = ?""")
                .params(assignmentId, userId, attemptNumber).query(AssignmentRepository::grade).optional();
    }

    void saveGrade(long assignmentId, long userId, int attemptNumber, Double grade, long graderId, Instant now) {
        int updated = jdbc.sql("""
                UPDATE asg_grade SET grade = ?, grader_id = ?, time_modified = ?
                WHERE assignment_id = ? AND user_id = ? AND attempt_number = ?""")
                .params(grade, graderId, ts(now), assignmentId, userId, attemptNumber).update();
        if (updated == 0) {
            jdbc.sql("""
                    INSERT INTO asg_grade (assignment_id, user_id, attempt_number, grade, grader_id, time_modified)
                    VALUES (?, ?, ?, ?, ?, ?)""")
                    .params(assignmentId, userId, attemptNumber, grade, graderId, ts(now)).update();
        }
    }

    // ---- mapping

    private static AssignmentRow assignment(ResultSet rs, int n) throws SQLException {
        String types = rs.getString("accepted_types");
        List<String> accepted = types == null || types.isBlank() ? List.of()
                : Arrays.stream(types.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return new AssignmentRow(rs.getLong("id"), rs.getLong("course_id"), rs.getString("name"), rs.getString("intro"),
                instant(rs, "allow_submissions_from"), instant(rs, "due_date"), instant(rs, "cutoff_date"),
                rs.getBoolean("submission_drafts"), rs.getBoolean("require_statement"), rs.getDouble("max_grade"),
                nullableDouble(rs, "grade_pass"), ReopenMethod.valueOf(rs.getString("reopen_method")),
                rs.getInt("max_attempts"), rs.getBoolean("online_text_enabled"), rs.getBoolean("file_enabled"),
                rs.getInt("max_files"), rs.getLong("max_bytes"), accepted,
                CompletionRule.valueOf(rs.getString("completion")), rs.getString("availability"),
                nullableLong(rs, "grade_item_id"));
    }

    private static SubmissionRow submission(ResultSet rs, int n) throws SQLException {
        return new SubmissionRow(rs.getLong("id"), rs.getLong("assignment_id"), rs.getLong("user_id"),
                rs.getInt("attempt_number"), SubmissionStatus.valueOf(rs.getString("status")), rs.getBoolean("latest"),
                rs.getString("online_text"), instant(rs, "time_created"), instant(rs, "time_modified"));
    }

    private static GradeRow grade(ResultSet rs, int n) throws SQLException {
        return new GradeRow(rs.getLong("user_id"), rs.getInt("attempt_number"), nullableDouble(rs, "grade"),
                nullableLong(rs, "grader_id"), instant(rs, "time_modified"));
    }
}
