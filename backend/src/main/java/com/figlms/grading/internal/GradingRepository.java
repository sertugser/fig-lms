package com.figlms.grading.internal;

import static com.figlms.Sql.nullableDouble;
import static com.figlms.Sql.nullableLong;
import static com.figlms.Sql.ts;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** The {@code grd_*} tables. */
@Repository
class GradingRepository {

    record CategoryRow(long id, long courseId, Long parentId, String fullname, Aggregation aggregation,
            boolean aggregateOnlyGraded, int dropLow, int keepHigh) {
    }

    record ItemRow(long id, long courseId, long categoryId, String itemname, String itemtype, String source,
            double gradeMin, double gradeMax, Double gradePass, double weight, boolean hidden, boolean locked,
            int sortorder) {

        boolean isTotal() {
            return "COURSE".equals(itemtype) || "CATEGORY".equals(itemtype);
        }
    }

    record GradeRow(long itemId, long userId, Double finalGrade, boolean hidden, boolean locked) {
    }

    private static final String CATEGORY_COLUMNS =
            "id, course_id, parent_id, fullname, aggregation, aggregate_only_graded, drop_low, keep_high";
    private static final String ITEM_COLUMNS = "id, course_id, category_id, itemname, itemtype, source, grade_min, "
            + "grade_max, grade_pass, weight, hidden, locked, sortorder";

    private final JdbcClient jdbc;

    GradingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<CategoryRow> categories(long courseId) {
        return jdbc.sql("SELECT " + CATEGORY_COLUMNS + " FROM grd_category WHERE course_id = ? ORDER BY id").param(courseId)
                .query(GradingRepository::categoryRow).list();
    }

    Optional<CategoryRow> category(long id) {
        return jdbc.sql("SELECT " + CATEGORY_COLUMNS + " FROM grd_category WHERE id = ?").param(id)
                .query(GradingRepository::categoryRow).optional();
    }

    Optional<CategoryRow> rootCategory(long courseId) {
        return jdbc.sql("SELECT " + CATEGORY_COLUMNS + " FROM grd_category WHERE course_id = ? AND parent_id IS NULL")
                .param(courseId).query(GradingRepository::categoryRow).optional();
    }

    long insertCategory(long courseId, Long parentId, String fullname, Aggregation aggregation) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO grd_category (course_id, parent_id, fullname, aggregation) VALUES (?, ?, ?, ?)")
                .params(courseId, parentId, fullname, aggregation.name()).update(keys, "id");
        return keys.getKey().longValue();
    }

    void updateCategory(long id, Aggregation aggregation, boolean aggregateOnlyGraded, int dropLow, int keepHigh) {
        jdbc.sql("UPDATE grd_category SET aggregation = ?, aggregate_only_graded = ?, drop_low = ?, keep_high = ? WHERE id = ?")
                .params(aggregation.name(), aggregateOnlyGraded, dropLow, keepHigh, id).update();
    }

    List<ItemRow> items(long courseId) {
        return jdbc.sql("SELECT " + ITEM_COLUMNS + " FROM grd_item WHERE course_id = ? ORDER BY sortorder, id").param(courseId)
                .query(GradingRepository::itemRow).list();
    }

    Optional<ItemRow> item(long id) {
        return jdbc.sql("SELECT " + ITEM_COLUMNS + " FROM grd_item WHERE id = ?").param(id)
                .query(GradingRepository::itemRow).optional();
    }

    Optional<ItemRow> totalItem(long categoryId) {
        return jdbc.sql("SELECT " + ITEM_COLUMNS + " FROM grd_item WHERE category_id = ? AND itemtype IN ('COURSE', 'CATEGORY')")
                .param(categoryId).query(GradingRepository::itemRow).optional();
    }

    long insertItem(long courseId, long categoryId, String name, String type, String source, double min, double max,
            Double pass, double weight) {
        int sortorder = jdbc.sql("SELECT COALESCE(MAX(sortorder), 0) + 1 FROM grd_item WHERE course_id = ?").param(courseId)
                .query(Integer.class).single();
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                        INSERT INTO grd_item (course_id, category_id, itemname, itemtype, source, grade_min, grade_max,
                                              grade_pass, weight, sortorder)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(courseId, categoryId, name, type, source, min, max, pass, weight, sortorder).update(keys, "id");
        return keys.getKey().longValue();
    }

    void updateItem(long id, String name, double max, Double pass, double weight, boolean hidden, boolean locked) {
        jdbc.sql("UPDATE grd_item SET itemname = ?, grade_max = ?, grade_pass = ?, weight = ?, hidden = ?, locked = ? WHERE id = ?")
                .params(name, max, pass, weight, hidden, locked, id).update();
    }

    List<GradeRow> grades(long courseId, long userId) {
        return jdbc.sql("""
                        SELECT g.item_id, g.user_id, g.final_grade, g.hidden, g.locked
                        FROM grd_grade g JOIN grd_item i ON i.id = g.item_id
                        WHERE i.course_id = ? AND g.user_id = ?""")
                .params(courseId, userId).query(GradingRepository::gradeRow).list();
    }

    Optional<GradeRow> grade(long itemId, long userId) {
        return jdbc.sql("SELECT item_id, user_id, final_grade, hidden, locked FROM grd_grade WHERE item_id = ? AND user_id = ?")
                .params(itemId, userId).query(GradingRepository::gradeRow).optional();
    }

    void upsertGrade(long itemId, long userId, Double raw, Double finalGrade, Long graderId, Instant now) {
        int updated = jdbc.sql("UPDATE grd_grade SET raw_grade = ?, final_grade = ?, grader_id = ?, time_modified = ? WHERE item_id = ? AND user_id = ?")
                .params(raw, finalGrade, graderId, ts(now), itemId, userId).update();
        if (updated == 0) {
            jdbc.sql("""
                            INSERT INTO grd_grade (item_id, user_id, raw_grade, final_grade, grader_id, time_modified)
                            VALUES (?, ?, ?, ?, ?, ?)""")
                    .params(itemId, userId, raw, finalGrade, graderId, ts(now)).update();
        }
    }

    void setGradeFlags(long itemId, long userId, boolean hidden, boolean locked, Instant now) {
        int updated = jdbc.sql("UPDATE grd_grade SET hidden = ?, locked = ? WHERE item_id = ? AND user_id = ?")
                .params(hidden, locked, itemId, userId).update();
        if (updated == 0) {
            jdbc.sql("INSERT INTO grd_grade (item_id, user_id, hidden, locked, time_modified) VALUES (?, ?, ?, ?, ?)")
                    .params(itemId, userId, hidden, locked, ts(now)).update();
        }
    }

    private static CategoryRow categoryRow(ResultSet rs, int n) throws SQLException {
        return new CategoryRow(rs.getLong("id"), rs.getLong("course_id"), nullableLong(rs, "parent_id"),
                rs.getString("fullname"), Aggregation.valueOf(rs.getString("aggregation")),
                rs.getBoolean("aggregate_only_graded"), rs.getInt("drop_low"), rs.getInt("keep_high"));
    }

    private static ItemRow itemRow(ResultSet rs, int n) throws SQLException {
        return new ItemRow(rs.getLong("id"), rs.getLong("course_id"), rs.getLong("category_id"), rs.getString("itemname"),
                rs.getString("itemtype"), rs.getString("source"), rs.getDouble("grade_min"), rs.getDouble("grade_max"),
                nullableDouble(rs, "grade_pass"), rs.getDouble("weight"), rs.getBoolean("hidden"), rs.getBoolean("locked"),
                rs.getInt("sortorder"));
    }

    private static GradeRow gradeRow(ResultSet rs, int n) throws SQLException {
        return new GradeRow(rs.getLong("item_id"), rs.getLong("user_id"), nullableDouble(rs, "final_grade"),
                rs.getBoolean("hidden"), rs.getBoolean("locked"));
    }
}
