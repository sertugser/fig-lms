package com.figlms.grading;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.figlms.TestClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** REQ-GRD-01..08 through {@code /api/grading}, on the CS101 sample gradebook (Assignment 1: 85, 72, 40). */
@SpringBootTest(properties = "spring.profiles.active=test")
@AutoConfigureMockMvc
@Import(TestClock.Config.class)
@Transactional
class GradebookTest {

    static final String PASSWORD = "test-password";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    ObjectMapper json;

    @Autowired
    TestClock clock;

    @BeforeEach
    void resetClock() {
        clock.reset();
    }

    static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String user, String body) {
        return request.with(httpBasic(user, PASSWORD)).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    long assignmentItem() {
        return jdbc.sql("SELECT id FROM grd_item WHERE itemname = 'Assignment 1'").query(Long.class).single();
    }

    long manualItem(String body) throws Exception {
        String response = mvc.perform(as(post("/api/grading/courses/CS101/items"), "teacher1", body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    @Test
    void normal_studentSeesOwnGradesAndTotal() throws Exception {
        mvc.perform(get("/api/grading/courses/CS101/users/student1").with(httpBasic("student1", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.name == 'Assignment 1')].grade").value(85.0))
                .andExpect(jsonPath("$.total").value(85.0))
                .andExpect(jsonPath("$.totalHidden").value(false));
    }

    @Test
    void normal_totalIsTheMeanOfScaledItems() throws Exception {
        long quiz = manualItem("{\"name\":\"Quiz\",\"gradeMax\":20}");
        mvc.perform(as(put("/api/grading/items/{id}/grades/student1", quiz), "teacher1", "{\"grade\":10}"))
                .andExpect(status().isNoContent());
        // mean(85/100, 10/20) = 0.675
        mvc.perform(get("/api/grading/courses/CS101/users/student1").with(httpBasic("student1", PASSWORD)))
                .andExpect(jsonPath("$.total").value(67.5));
    }

    @Test
    void boundary_gradeIsLimitedToTheItemRange() throws Exception {
        long quiz = manualItem("{\"name\":\"Quiz\",\"gradeMax\":20}");
        mvc.perform(as(put("/api/grading/items/{id}/grades/student2", quiz), "teacher1", "{\"grade\":25}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/grading/courses/CS101/users/student2").with(httpBasic("student2", PASSWORD)))
                .andExpect(jsonPath("$.items[?(@.name == 'Quiz')].grade").value(20.0));
    }

    @Test
    void invalid_activityGradesAreChangedInTheActivity() throws Exception {
        mvc.perform(as(put("/api/grading/items/{id}/grades/student1", assignmentItem()), "teacher1", "{\"grade\":10}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("GRADE_IN_ACTIVITY"));
    }

    @Test
    void normal_hiddenItemIsLeftOutAndTheTotalWithheld() throws Exception {
        mvc.perform(as(put("/api/grading/items/{id}", assignmentItem()), "teacher1", "{\"hidden\":true}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/grading/courses/CS101/users/student1").with(httpBasic("student1", PASSWORD)))
                .andExpect(jsonPath("$.items[?(@.name == 'Assignment 1')]").doesNotExist())
                .andExpect(jsonPath("$.totalHidden").value(true));
        mvc.perform(get("/api/grading/courses/CS101/users/student1").with(httpBasic("teacher1", PASSWORD)))
                .andExpect(jsonPath("$.items[?(@.name == 'Assignment 1')].grade").value(85.0));
    }

    @Test
    void invalid_showGradesOffHidesTheReportFromStudents() throws Exception {
        mvc.perform(as(put("/api/course/courses/CS101/show-grades"), "teacher1", "{\"showGrades\":false}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/grading/courses/CS101/users/student1").with(httpBasic("student1", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("NO_PERMISSION_TO_VIEW_GRADES"));
    }

    @Test
    void invalid_studentCannotSeeAnotherStudent() throws Exception {
        mvc.perform(get("/api/grading/courses/CS101/users/student2").with(httpBasic("student1", PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void normal_graderReportForTeachersOnly() throws Exception {
        mvc.perform(get("/api/grading/courses/CS101/report").with(httpBasic("teacher1", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students.length()").value(5));
        mvc.perform(get("/api/grading/courses/CS101/report").with(httpBasic("student1", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("NO_PERMISSIONS"));
    }

    @Test
    void normal_dropLowestInASubCategory() throws Exception {
        String category = mvc.perform(as(post("/api/grading/courses/CS101/categories"), "teacher1",
                        "{\"fullname\":\"Quizzes\",\"aggregation\":\"MEAN\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long categoryId = json.readTree(category).get("id").asLong();
        mvc.perform(as(put("/api/grading/courses/CS101/categories/{id}", categoryId), "teacher1", "{\"dropLow\":1}"))
                .andExpect(status().isOk());
        long q1 = manualItem("{\"name\":\"Q1\",\"categoryId\":" + categoryId + "}");
        long q2 = manualItem("{\"name\":\"Q2\",\"categoryId\":" + categoryId + "}");
        mvc.perform(as(put("/api/grading/items/{id}/grades/student3", q1), "teacher1", "{\"grade\":20}"));
        mvc.perform(as(put("/api/grading/items/{id}/grades/student3", q2), "teacher1", "{\"grade\":80}"));
        // Quizzes: drop 20 -> 80; course: mean(40/100, 80/100) = 60
        mvc.perform(get("/api/grading/courses/CS101/users/student3").with(httpBasic("teacher1", PASSWORD)))
                .andExpect(jsonPath("$.total").value(60.0));
    }
}
