package com.figlms.advising.internal;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Reads the advising tables ({@code adv_*}, see {@code db/migration}).
 *
 * <p>For now the advising module keeps its own simple course, student and completion tables (#6). They move behind
 * the {@code api} packages of the course and grading modules once those modules store data.
 */
@Repository
class AdvisingRepository {

    private final JdbcClient jdbc;

    AdvisingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    boolean studentExists(String username) {
        return jdbc.sql("SELECT COUNT(*) FROM adv_student WHERE username = ?").param(username)
                .query(Integer.class).single() > 0;
    }

    boolean courseExists(String shortname) {
        return jdbc.sql("SELECT COUNT(*) FROM adv_course WHERE shortname = ?").param(shortname)
                .query(Integer.class).single() > 0;
    }

    List<String> prerequisitesOf(String shortname) {
        return jdbc.sql("SELECT prerequisite FROM adv_prerequisite WHERE course = ? ORDER BY prerequisite")
                .param(shortname).query(String.class).list();
    }

    Set<String> completedCourses(String username) {
        return new HashSet<>(jdbc.sql(
                        "SELECT course FROM adv_course_completion WHERE username = ? AND time_completed IS NOT NULL")
                .param(username).query(String.class).list());
    }
}
