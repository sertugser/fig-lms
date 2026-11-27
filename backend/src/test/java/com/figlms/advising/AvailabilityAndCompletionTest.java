package com.figlms.advising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.figlms.ApiException;
import com.figlms.TestClock;
import com.figlms.advising.api.Availability;
import com.figlms.advising.api.AvailabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * REQ-ADV-01..07 (access restrictions) and REQ-ADV-05 (course completion) on the sample data: Assignment 1 is
 * complete-and-passed for student1 and student2, complete-and-failed for student3, not done by student4.
 */
@SpringBootTest(properties = "spring.profiles.active=test")
@AutoConfigureMockMvc
@Import(TestClock.Config.class)
@Transactional
class AvailabilityAndCompletionTest {

    static final String PASSWORD = "test-password";
    /** 2026-11-01T00:00:00Z */
    static final long NOV_1 = 1793491200L;

    @Autowired
    AvailabilityService availability;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestClock clock;

    long cs101;
    String activity;
    long gradeItem;

    @BeforeEach
    void setUp() {
        clock.reset();
        cs101 = jdbc.sql("SELECT id FROM crs_course WHERE shortname = 'CS101'").query(Long.class).single();
        activity = jdbc.sql("SELECT activity_key FROM adv_activity WHERE name = 'Assignment 1'").query(String.class).single();
        gradeItem = jdbc.sql("SELECT id FROM grd_item WHERE itemname = 'Assignment 1'").query(Long.class).single();
    }

    Availability eval(String json, String user) {
        return availability.evaluate(json, cs101, user);
    }

    String completion(int e) {
        return "{\"type\":\"completion\",\"cm\":\"" + activity + "\",\"e\":" + e + "}";
    }

    @Test
    void normal_noRestrictionIsAvailable() {
        assertThat(eval(null, "student4")).isEqualTo(Availability.open());
    }

    @Test
    void normal_completionCondition() {
        String tree = "{\"op\":\"&\",\"showc\":[true],\"c\":[" + completion(1) + "]}";
        assertThat(eval(tree, "student1").available()).isTrue();
        assertThat(eval(tree, "student3").available()).isFalse(); // complete but failed
        Availability s4 = eval(tree, "student4");
        assertThat(s4.available()).isFalse();
        assertThat(s4.shownOnCoursePage()).isTrue();
        assertThat(s4.information()).startsWith("Not available unless").contains("Assignment 1");
    }

    @Test
    void normal_completeAndFailedCondition() {
        String tree = "{\"op\":\"&\",\"showc\":[true],\"c\":[" + completion(3) + "]}";
        assertThat(eval(tree, "student3").available()).isTrue();
        assertThat(eval(tree, "student1").available()).isFalse();
    }

    @Test
    void boundary_dateFromIsInclusive() {
        String tree = "{\"op\":\"&\",\"showc\":[true],\"c\":[{\"type\":\"date\",\"d\":\">=\",\"t\":" + NOV_1 + "}]}";
        clock.set("2026-10-31T23:59:59Z");
        assertThat(eval(tree, "student1").available()).isFalse();
        clock.set("2026-11-01T00:00:00Z");
        assertThat(eval(tree, "student1").available()).isTrue();
    }

    @Test
    void boundary_dateUntilIsExclusive() {
        String tree = "{\"op\":\"&\",\"showc\":[true],\"c\":[{\"type\":\"date\",\"d\":\"<\",\"t\":" + NOV_1 + "}]}";
        clock.set("2026-10-31T23:59:59Z");
        assertThat(eval(tree, "student1").available()).isTrue();
        clock.set("2026-11-01T00:00:00Z");
        assertThat(eval(tree, "student1").available()).isFalse();
    }

    @Test
    void boundary_gradeMinimumIsInclusive() {
        String tree = "{\"op\":\"&\",\"showc\":[true],\"c\":[{\"type\":\"grade\",\"id\":" + gradeItem + ",\"min\":72}]}";
        assertThat(eval(tree, "student2").available()).isTrue(); // 72
        assertThat(eval(tree, "student3").available()).isFalse(); // 40
        assertThat(eval(tree, "student4").available()).isFalse(); // no grade
    }

    @Test
    void normal_orNeedsOneCondition() {
        String tree = "{\"op\":\"|\",\"show\":true,\"c\":[" + completion(1) + ",{\"type\":\"grade\",\"id\":" + gradeItem
                + ",\"min\":30}]}";
        assertThat(eval(tree, "student3").available()).isTrue(); // fails completion, passes grade >= 30%
        assertThat(eval(tree, "student4").available()).isFalse();
    }

    @Test
    void normal_notAndIsTrueUnlessEveryChildHolds() {
        // !& = NOT(a AND b) = (NOT a) OR (NOT b), Moodle tree::get_logic_flags().
        clock.set("2026-11-27T00:00:00Z");
        String tree = "{\"op\":\"!&\",\"show\":true,\"c\":[" + completion(1)
                + ",{\"type\":\"date\",\"d\":\">=\",\"t\":" + NOV_1 + "}]}";
        assertThat(eval(tree, "student1").available()).isFalse(); // both hold
        assertThat(eval(tree, "student4").available()).isTrue(); // completion does not hold
    }

    @Test
    void normal_hiddenWhenTheFailedConditionHasShowFalse() {
        String tree = "{\"op\":\"&\",\"showc\":[false],\"c\":[" + completion(1) + "]}";
        Availability a = eval(tree, "student4");
        assertThat(a.available()).isFalse();
        assertThat(a.shownOnCoursePage()).isFalse();
    }

    @Test
    void normal_teachersIgnoreRestrictions() {
        String tree = "{\"op\":\"&\",\"showc\":[true],\"c\":[" + completion(1) + "]}";
        assertThat(eval(tree, "teacher1").available()).isTrue();
    }

    @Test
    void invalid_unknownOperator() {
        assertThatThrownBy(() -> availability.validate("{\"op\":\"xor\",\"c\":[]}"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("INVALID_AVAILABILITY"));
    }

    @Test
    void invalid_deletedActivityIsNeverMet() {
        jdbc.sql("UPDATE adv_activity SET deleted = TRUE WHERE activity_key = ?").param(activity).update();
        String tree = "{\"op\":\"&\",\"showc\":[true],\"c\":[" + completion(1) + "]}";
        assertThat(eval(tree, "student1").available()).isFalse();
    }

    // ---- course completion

    @Test
    void normal_teacherMarkCompletesCs201WhenCs101IsComplete() throws Exception {
        mvc.perform(post("/api/advising/courses/CS201/completion/student1/mark").with(httpBasic("teacher1", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.criteria.length()").value(2));
    }

    @Test
    void normal_markAloneIsNotEnoughWithoutThePrerequisiteCourse() throws Exception {
        mvc.perform(post("/api/advising/courses/CS201/completion/student3/mark").with(httpBasic("teacher1", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(false))
                .andExpect(jsonPath("$.criteria[?(@.type == 'COURSE')].met").value(false));
    }

    @Test
    void invalid_studentCannotMarkCompletion() throws Exception {
        mvc.perform(post("/api/advising/courses/CS201/completion/student1/mark").with(httpBasic("student1", PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalid_studentCannotSeeAnotherStudentsCompletion() throws Exception {
        mvc.perform(get("/api/advising/courses/CS101/completion/student2").with(httpBasic("student1", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("CANNOT_VIEW_REPORT"));
    }

    @Test
    void invalid_prerequisiteCycleIsRejected() throws Exception {
        mvc.perform(put("/api/advising/courses/CS101/prerequisites").with(httpBasic("admin", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prerequisites\":[\"CS201\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PREREQUISITE_CYCLE"));
    }
}
