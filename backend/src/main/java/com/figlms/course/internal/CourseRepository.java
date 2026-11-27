package com.figlms.course.internal;

import static com.figlms.Sql.instant;
import static com.figlms.Sql.nullableLong;
import static com.figlms.Sql.ts;

import com.figlms.course.api.CourseSummary;
import com.figlms.course.api.EnrolmentMethod;
import com.figlms.identity.api.Role;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** All {@code crs_*} tables. */
@Repository
class CourseRepository {

    record CategoryRow(long id, String name, String idnumber, Long parentId, int depth, String path, boolean visible,
            boolean visibleOld) {
    }

    record MethodRow(long id, long courseId, EnrolmentMethod method, boolean enabled, Role defaultRole,
            Long enrolPeriodSeconds, Instant enrolStart, Instant enrolEnd, String enrolmentKey, int maxEnrolled,
            boolean allowNew) {
    }

    record EnrolmentRow(long id, long methodId, EnrolmentMethod method, boolean methodEnabled, long courseId,
            long userId, String status, Instant timeStart, Instant timeEnd) {

        /** REQ-CRS-06: active, method enabled, {@code timestart < now} (strict), {@code timeend} unset or after now. */
        boolean isActive(Instant now) {
            return "ACTIVE".equals(status) && methodEnabled
                    && (timeStart == null || timeStart.isBefore(now))
                    && (timeEnd == null || timeEnd.isAfter(now));
        }
    }

    private static final String COURSE_COLUMNS =
            "id, shortname, fullname, idnumber, category_id, startdate, enddate, visible, show_grades";
    private static final String CATEGORY_COLUMNS = "id, name, idnumber, parent_id, depth, path, visible, visible_old";
    private static final String METHOD_COLUMNS = "id, course_id, method, enabled, default_role, enrol_period_seconds, "
            + "enrol_start, enrol_end, enrolment_key, max_enrolled, allow_new";
    private static final String ENROLMENT_SELECT = """
            SELECT ue.id, ue.method_id, m.method, m.enabled, m.course_id, ue.user_id, ue.status, ue.time_start, ue.time_end
            FROM crs_user_enrolment ue JOIN crs_enrol_method m ON m.id = ue.method_id""";

    private final JdbcClient jdbc;

    CourseRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---- categories

    Optional<CategoryRow> category(long id) {
        return jdbc.sql("SELECT " + CATEGORY_COLUMNS + " FROM crs_category WHERE id = ?").param(id)
                .query(CourseRepository::categoryRow).optional();
    }

    List<CategoryRow> categories() {
        return jdbc.sql("SELECT " + CATEGORY_COLUMNS + " FROM crs_category ORDER BY path").query(CourseRepository::categoryRow)
                .list();
    }

    boolean categoryIdnumberTaken(String idnumber) {
        return jdbc.sql("SELECT COUNT(*) FROM crs_category WHERE idnumber = ?").param(idnumber)
                .query(Integer.class).single() > 0;
    }

    long insertCategory(String name, String idnumber, Long parentId, int depth, boolean visible, boolean visibleOld) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO crs_category (name, idnumber, parent_id, depth, path, visible, visible_old) VALUES (?, ?, ?, ?, '', ?, ?)")
                .params(name, idnumber, parentId, depth, visible, visibleOld).update(keys, "id");
        return keys.getKey().longValue();
    }

    void updateCategoryTree(long id, Long parentId, int depth, String path, boolean visible) {
        jdbc.sql("UPDATE crs_category SET parent_id = ?, depth = ?, path = ?, visible = ? WHERE id = ?")
                .params(parentId, depth, path, visible, id).update();
    }

    void setCategoryPath(long id, String path) {
        jdbc.sql("UPDATE crs_category SET path = ? WHERE id = ?").params(path, id).update();
    }

    int countChildren(long categoryId) {
        return jdbc.sql("SELECT (SELECT COUNT(*) FROM crs_category WHERE parent_id = ?) + (SELECT COUNT(*) FROM crs_course WHERE category_id = ?)")
                .params(categoryId, categoryId).query(Integer.class).single();
    }

    void moveCourses(long fromCategoryId, long toCategoryId) {
        jdbc.sql("UPDATE crs_course SET category_id = ? WHERE category_id = ?").params(toCategoryId, fromCategoryId).update();
    }

    void deleteCategory(long id) {
        jdbc.sql("DELETE FROM crs_category WHERE id = ?").param(id).update();
    }

    // ---- courses

    Optional<CourseSummary> courseByShortname(String shortname) {
        return jdbc.sql("SELECT " + COURSE_COLUMNS + " FROM crs_course WHERE shortname = ?").param(shortname)
                .query(CourseRepository::course).optional();
    }

    Optional<CourseSummary> course(long id) {
        return jdbc.sql("SELECT " + COURSE_COLUMNS + " FROM crs_course WHERE id = ?").param(id)
                .query(CourseRepository::course).optional();
    }

    List<CourseSummary> courses() {
        return jdbc.sql("SELECT " + COURSE_COLUMNS + " FROM crs_course ORDER BY shortname").query(CourseRepository::course)
                .list();
    }

    boolean shortnameTaken(String shortname) {
        return jdbc.sql("SELECT COUNT(*) FROM crs_course WHERE shortname = ?").param(shortname)
                .query(Integer.class).single() > 0;
    }

    boolean courseIdnumberTaken(String idnumber) {
        return jdbc.sql("SELECT COUNT(*) FROM crs_course WHERE idnumber = ?").param(idnumber)
                .query(Integer.class).single() > 0;
    }

    long insertCourse(long categoryId, String fullname, String shortname, String idnumber, Instant start, Instant end,
            boolean visible, Instant now) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                        INSERT INTO crs_course (category_id, fullname, shortname, idnumber, startdate, enddate, visible, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(categoryId, fullname, shortname, idnumber, ts(start), ts(end), visible, ts(now))
                .update(keys, "id");
        return keys.getKey().longValue();
    }

    void setShowGrades(long courseId, boolean showGrades) {
        jdbc.sql("UPDATE crs_course SET show_grades = ? WHERE id = ?").params(showGrades, courseId).update();
    }

    // ---- enrolment methods

    void insertMethod(long courseId, EnrolmentMethod method, boolean enabled) {
        jdbc.sql("INSERT INTO crs_enrol_method (course_id, method, enabled, default_role) VALUES (?, ?, ?, 'STUDENT')")
                .params(courseId, method.name(), enabled).update();
    }

    Optional<MethodRow> method(long courseId, EnrolmentMethod method) {
        return jdbc.sql("SELECT " + METHOD_COLUMNS + " FROM crs_enrol_method WHERE course_id = ? AND method = ?")
                .params(courseId, method.name()).query(CourseRepository::methodRow).optional();
    }

    List<MethodRow> methods(long courseId) {
        return jdbc.sql("SELECT " + METHOD_COLUMNS + " FROM crs_enrol_method WHERE course_id = ? ORDER BY id")
                .param(courseId).query(CourseRepository::methodRow).list();
    }

    void updateSelfMethod(long methodId, boolean enabled, Long periodSeconds, Instant start, Instant end, String key,
            int maxEnrolled, boolean allowNew) {
        jdbc.sql("""
                        UPDATE crs_enrol_method SET enabled = ?, enrol_period_seconds = ?, enrol_start = ?, enrol_end = ?,
                               enrolment_key = ?, max_enrolled = ?, allow_new = ?
                        WHERE id = ?""")
                .params(enabled, periodSeconds, ts(start), ts(end), key, maxEnrolled, allowNew, methodId).update();
    }

    int countEnrolments(long methodId) {
        return jdbc.sql("SELECT COUNT(*) FROM crs_user_enrolment WHERE method_id = ?").param(methodId)
                .query(Integer.class).single();
    }

    // ---- user enrolments and roles

    Optional<EnrolmentRow> enrolment(long methodId, long userId) {
        return jdbc.sql(ENROLMENT_SELECT + " WHERE ue.method_id = ? AND ue.user_id = ?").params(methodId, userId)
                .query(CourseRepository::enrolmentRow).optional();
    }

    List<EnrolmentRow> enrolments(long courseId, long userId) {
        return jdbc.sql(ENROLMENT_SELECT + " WHERE m.course_id = ? AND ue.user_id = ? ORDER BY ue.id")
                .params(courseId, userId).query(CourseRepository::enrolmentRow).list();
    }

    List<EnrolmentRow> enrolmentsOfCourse(long courseId) {
        return jdbc.sql(ENROLMENT_SELECT + " WHERE m.course_id = ? ORDER BY ue.user_id, ue.id").param(courseId)
                .query(CourseRepository::enrolmentRow).list();
    }

    List<EnrolmentRow> enrolmentsOfUser(long userId) {
        return jdbc.sql(ENROLMENT_SELECT + " WHERE ue.user_id = ? ORDER BY m.course_id, ue.id").param(userId)
                .query(CourseRepository::enrolmentRow).list();
    }

    void insertEnrolment(long methodId, long userId, Instant start, Instant end, Instant now, long modifiedBy) {
        jdbc.sql("""
                        INSERT INTO crs_user_enrolment (method_id, user_id, status, time_start, time_end, created_at, modified_by)
                        VALUES (?, ?, 'ACTIVE', ?, ?, ?, ?)""")
                .params(methodId, userId, ts(start), ts(end), ts(now), modifiedBy).update();
    }

    void updateEnrolment(long id, String status, Instant start, Instant end, long modifiedBy) {
        jdbc.sql("UPDATE crs_user_enrolment SET status = ?, time_start = ?, time_end = ?, modified_by = ? WHERE id = ?")
                .params(status, ts(start), ts(end), modifiedBy, id).update();
    }

    void setEnrolmentStatus(long id, String status, long modifiedBy) {
        jdbc.sql("UPDATE crs_user_enrolment SET status = ?, modified_by = ? WHERE id = ?")
                .params(status, modifiedBy, id).update();
    }

    Set<Role> roles(long courseId, long userId) {
        List<String> names = jdbc.sql("SELECT role FROM crs_role_assignment WHERE course_id = ? AND user_id = ?")
                .params(courseId, userId).query(String.class).list();
        Set<Role> roles = EnumSet.noneOf(Role.class);
        names.forEach(n -> roles.add(Role.valueOf(n)));
        return roles;
    }

    List<Long> usersWithRole(long courseId, Role role) {
        return jdbc.sql("SELECT user_id FROM crs_role_assignment WHERE course_id = ? AND role = ? ORDER BY user_id")
                .params(courseId, role.name()).query(Long.class).list();
    }

    void assignRole(long courseId, long userId, Role role) {
        int exists = jdbc.sql("SELECT COUNT(*) FROM crs_role_assignment WHERE course_id = ? AND user_id = ? AND role = ?")
                .params(courseId, userId, role.name()).query(Integer.class).single();
        if (exists == 0) {
            jdbc.sql("INSERT INTO crs_role_assignment (course_id, user_id, role) VALUES (?, ?, ?)")
                    .params(courseId, userId, role.name()).update();
        }
    }

    // ---- mapping

    private static CategoryRow categoryRow(ResultSet rs, int n) throws SQLException {
        return new CategoryRow(rs.getLong("id"), rs.getString("name"), rs.getString("idnumber"),
                nullableLong(rs, "parent_id"), rs.getInt("depth"), rs.getString("path"), rs.getBoolean("visible"),
                rs.getBoolean("visible_old"));
    }

    private static CourseSummary course(ResultSet rs, int n) throws SQLException {
        return new CourseSummary(rs.getLong("id"), rs.getString("shortname"), rs.getString("fullname"),
                rs.getString("idnumber"), rs.getLong("category_id"), instant(rs, "startdate"), instant(rs, "enddate"),
                rs.getBoolean("visible"), rs.getBoolean("show_grades"));
    }

    private static MethodRow methodRow(ResultSet rs, int n) throws SQLException {
        return new MethodRow(rs.getLong("id"), rs.getLong("course_id"), EnrolmentMethod.valueOf(rs.getString("method")),
                rs.getBoolean("enabled"), Role.valueOf(rs.getString("default_role")),
                nullableLong(rs, "enrol_period_seconds"), instant(rs, "enrol_start"), instant(rs, "enrol_end"),
                rs.getString("enrolment_key"), rs.getInt("max_enrolled"), rs.getBoolean("allow_new"));
    }

    private static EnrolmentRow enrolmentRow(ResultSet rs, int n) throws SQLException {
        return new EnrolmentRow(rs.getLong("id"), rs.getLong("method_id"), EnrolmentMethod.valueOf(rs.getString("method")),
                rs.getBoolean("enabled"), rs.getLong("course_id"), rs.getLong("user_id"), rs.getString("status"),
                instant(rs, "time_start"), instant(rs, "time_end"));
    }
}
