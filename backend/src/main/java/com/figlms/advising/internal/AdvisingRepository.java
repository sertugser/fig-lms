package com.figlms.advising.internal;

import static com.figlms.Sql.instant;
import static com.figlms.Sql.nullableDouble;
import static com.figlms.Sql.nullableLong;
import static com.figlms.Sql.ts;

import com.figlms.advising.api.CompletionState;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The {@code adv_*} tables (see {@code db/migration} V1, V2 and V6). */
@Repository
class AdvisingRepository {

    record ActivityRow(String key, long courseId, String name, boolean deleted) {
    }

    record CriterionRow(long id, long courseId, CriterionType type, String activityKey, Long otherCourseId, String role,
            Double gradePass, Instant timeEnd) {
    }

    private final JdbcClient jdbc;

    AdvisingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---- prerequisites (REQ-ADV-09)

    List<Long> prerequisitesOf(long courseId) {
        return jdbc.sql("SELECT prerequisite_id FROM adv_course_prerequisite WHERE course_id = ?").param(courseId)
                .query(Long.class).list();
    }

    void replacePrerequisites(long courseId, List<Long> prerequisiteIds) {
        jdbc.sql("DELETE FROM adv_course_prerequisite WHERE course_id = ?").param(courseId).update();
        for (Long p : prerequisiteIds) {
            jdbc.sql("INSERT INTO adv_course_prerequisite (course_id, prerequisite_id) VALUES (?, ?)").params(courseId, p)
                    .update();
        }
    }

    // ---- course completion

    Set<Long> completedCourses(long userId) {
        return new HashSet<>(jdbc.sql("SELECT course_id FROM adv_course_completion WHERE user_id = ? AND time_completed IS NOT NULL")
                .param(userId).query(Long.class).list());
    }

    Optional<Instant> courseCompletedAt(long courseId, long userId) {
        return jdbc.sql("SELECT time_completed FROM adv_course_completion WHERE course_id = ? AND user_id = ? AND time_completed IS NOT NULL")
                .params(courseId, userId).query((rs, n) -> instant(rs, "time_completed")).optional();
    }

    void markCourseComplete(long courseId, long userId, Instant at) {
        int updated = jdbc.sql("UPDATE adv_course_completion SET time_completed = ? WHERE course_id = ? AND user_id = ? AND time_completed IS NULL")
                .params(ts(at), courseId, userId).update();
        if (updated == 0 && courseCompletedAt(courseId, userId).isEmpty()) {
            jdbc.sql("INSERT INTO adv_course_completion (course_id, user_id, time_completed) VALUES (?, ?, ?)")
                    .params(courseId, userId, ts(at)).update();
        }
    }

    /** Courses whose completion depends on {@code courseId} (criterion "completion of other courses"). */
    List<Long> coursesDependingOn(long courseId) {
        return jdbc.sql("SELECT DISTINCT course_id FROM adv_completion_criterion WHERE criterion_type = 'COURSE' AND other_course_id = ?")
                .param(courseId).query(Long.class).list();
    }

    // ---- activities

    Optional<ActivityRow> activity(String key) {
        return jdbc.sql("SELECT activity_key, course_id, name, deleted FROM adv_activity WHERE activity_key = ?").param(key)
                .query((rs, n) -> new ActivityRow(rs.getString("activity_key"), rs.getLong("course_id"),
                        rs.getString("name"), rs.getBoolean("deleted")))
                .optional();
    }

    void upsertActivity(String key, long courseId, String name) {
        int updated = jdbc.sql("UPDATE adv_activity SET course_id = ?, name = ?, deleted = FALSE WHERE activity_key = ?")
                .params(courseId, name, key).update();
        if (updated == 0) {
            jdbc.sql("INSERT INTO adv_activity (activity_key, course_id, name) VALUES (?, ?, ?)").params(key, courseId, name)
                    .update();
        }
    }

    void markActivityDeleted(String key) {
        jdbc.sql("UPDATE adv_activity SET deleted = TRUE WHERE activity_key = ?").param(key).update();
    }

    CompletionState activityState(String key, long userId) {
        return jdbc.sql("SELECT state FROM adv_activity_completion WHERE activity_key = ? AND user_id = ?")
                .params(key, userId).query(String.class).optional().map(CompletionState::valueOf)
                .orElse(CompletionState.INCOMPLETE);
    }

    void upsertActivityState(String key, long userId, CompletionState state, Instant now) {
        int updated = jdbc.sql("UPDATE adv_activity_completion SET state = ?, time_modified = ? WHERE activity_key = ? AND user_id = ?")
                .params(state.name(), ts(now), key, userId).update();
        if (updated == 0) {
            jdbc.sql("INSERT INTO adv_activity_completion (activity_key, user_id, state, time_modified) VALUES (?, ?, ?, ?)")
                    .params(key, userId, state.name(), ts(now)).update();
        }
    }

    /** Courses that have a completion criterion on this activity. */
    List<Long> coursesWithActivityCriterion(String key) {
        return jdbc.sql("SELECT DISTINCT course_id FROM adv_completion_criterion WHERE criterion_type = 'ACTIVITY' AND activity_key = ?")
                .param(key).query(Long.class).list();
    }

    // ---- criteria

    List<CriterionRow> criteria(long courseId) {
        return jdbc.sql("""
                        SELECT id, course_id, criterion_type, activity_key, other_course_id, role, grade_pass, time_end
                        FROM adv_completion_criterion WHERE course_id = ? ORDER BY id""")
                .param(courseId).query(AdvisingRepository::criterion).list();
    }

    Optional<CriterionRow> criterion(long id) {
        return jdbc.sql("""
                        SELECT id, course_id, criterion_type, activity_key, other_course_id, role, grade_pass, time_end
                        FROM adv_completion_criterion WHERE id = ?""")
                .param(id).query(AdvisingRepository::criterion).optional();
    }

    void insertCriterion(long courseId, CriterionType type, String activityKey, Long otherCourseId, String role,
            Double gradePass, Instant timeEnd) {
        jdbc.sql("""
                        INSERT INTO adv_completion_criterion (course_id, criterion_type, activity_key, other_course_id, role, grade_pass, time_end)
                        VALUES (?, ?, ?, ?, ?, ?, ?)""")
                .params(courseId, type.name(), activityKey, otherCourseId, role, gradePass, ts(timeEnd)).update();
    }

    void deleteCriteria(long courseId) {
        jdbc.sql("DELETE FROM adv_criterion_mark WHERE criterion_id IN (SELECT id FROM adv_completion_criterion WHERE course_id = ?)")
                .param(courseId).update();
        jdbc.sql("DELETE FROM adv_completion_criterion WHERE course_id = ?").param(courseId).update();
        jdbc.sql("DELETE FROM adv_completion_aggregation WHERE course_id = ?").param(courseId).update();
    }

    String aggregation(long courseId, String type) {
        return jdbc.sql("SELECT method FROM adv_completion_aggregation WHERE course_id = ? AND criterion_type = ?")
                .params(courseId, type).query(String.class).optional().orElse("ALL");
    }

    void setAggregation(long courseId, String type, String method) {
        jdbc.sql("DELETE FROM adv_completion_aggregation WHERE course_id = ? AND criterion_type = ?").params(courseId, type)
                .update();
        jdbc.sql("INSERT INTO adv_completion_aggregation (course_id, criterion_type, method) VALUES (?, ?, ?)")
                .params(courseId, type, method).update();
    }

    boolean isMarked(long criterionId, long userId) {
        return jdbc.sql("SELECT COUNT(*) FROM adv_criterion_mark WHERE criterion_id = ? AND user_id = ?")
                .params(criterionId, userId).query(Integer.class).single() > 0;
    }

    void mark(long criterionId, long userId, long markedBy, Instant now) {
        if (!isMarked(criterionId, userId)) {
            jdbc.sql("INSERT INTO adv_criterion_mark (criterion_id, user_id, marked_by, time_completed) VALUES (?, ?, ?, ?)")
                    .params(criterionId, userId, markedBy, ts(now)).update();
        }
    }

    private static CriterionRow criterion(ResultSet rs, int n) throws SQLException {
        return new CriterionRow(rs.getLong("id"), rs.getLong("course_id"), CriterionType.valueOf(rs.getString("criterion_type")),
                rs.getString("activity_key"), nullableLong(rs, "other_course_id"), rs.getString("role"),
                nullableDouble(rs, "grade_pass"), instant(rs, "time_end"));
    }
}
