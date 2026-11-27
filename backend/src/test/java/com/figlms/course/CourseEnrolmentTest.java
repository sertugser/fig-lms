package com.figlms.course;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.figlms.TestClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** REQ-CRS-01..10 through {@code /api/course}. */
@SpringBootTest(properties = "spring.profiles.active=test")
@AutoConfigureMockMvc
@Import(TestClock.Config.class)
@Transactional
class CourseEnrolmentTest {

    static final String PASSWORD = "test-password";

    @Autowired
    MockMvc mvc;

    @Autowired
    TestClock clock;

    @BeforeEach
    void resetClock() {
        clock.reset();
    }

    static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String user, String body) {
        return request.with(httpBasic(user, PASSWORD)).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void normal_adminCreatesACourseAndIsEnrolledAsEditingTeacher() throws Exception {
        mvc.perform(json(post("/api/course/courses"), "admin", """
                        {"fullname":"Algorithms","shortname":"CS301","categoryId":1,
                         "startdate":"2026-09-28T00:00:00Z","enddate":"2027-01-22T00:00:00Z"}"""))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/course/courses/CS301/participants").with(httpBasic("admin", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("admin"))
                .andExpect(jsonPath("$[0].roles[0]").value("EDITING_TEACHER"));
    }

    @Test
    void invalid_shortnameIsUnique() throws Exception {
        mvc.perform(json(post("/api/course/courses"), "admin", """
                        {"fullname":"Copy","shortname":"CS101","categoryId":1,"startdate":"2026-09-28T00:00:00Z"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SHORTNAME_TAKEN"));
    }

    @Test
    void invalid_endDateBeforeStartDate() throws Exception {
        mvc.perform(json(post("/api/course/courses"), "admin", """
                        {"fullname":"Bad","shortname":"BAD1","categoryId":1,
                         "startdate":"2026-09-28T00:00:00Z","enddate":"2026-09-01T00:00:00Z"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("END_BEFORE_START"));
    }

    @Test
    void invalid_teacherCannotCreateCourses() throws Exception {
        mvc.perform(json(post("/api/course/courses"), "teacher1", """
                        {"fullname":"X","shortname":"X1","categoryId":1,"startdate":"2026-09-28T00:00:00Z"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void normal_editingTeacherEnrolsAStudent() throws Exception {
        mvc.perform(json(post("/api/course/courses/MATH101/enrolments"), "teacher2",
                        "{\"username\":\"student1\",\"role\":\"STUDENT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true));
        mvc.perform(get("/api/course/courses/MATH101/access").with(httpBasic("student1", PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void invalid_editingTeacherCannotGiveTheManagerRole() throws Exception {
        mvc.perform(json(post("/api/course/courses/MATH101/enrolments"), "teacher2",
                        "{\"username\":\"student1\",\"role\":\"MANAGER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ROLE_NOT_ASSIGNABLE"));
    }

    @Test
    void invalid_notEnrolledUserCannotEnterTheCourse() throws Exception {
        mvc.perform(get("/api/course/courses/MATH101/access").with(httpBasic("student1", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("NOT_ENROLLED"));
    }

    @Test
    void boundary_suspendedEnrolmentGivesNoAccess() throws Exception {
        mvc.perform(json(put("/api/course/courses/CS101/enrolments/student5/suspended"), "teacher1",
                        "{\"suspended\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/course/courses/CS101/access").with(httpBasic("student5", PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void boundary_enrolmentStartsStrictlyAfterItsStartTime() throws Exception {
        mvc.perform(json(post("/api/course/courses/MATH101/enrolments"), "teacher2",
                        "{\"username\":\"student1\",\"role\":\"STUDENT\",\"timeStart\":\"2026-12-01T00:00:00Z\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(false));
        clock.set("2026-12-01T00:00:00Z");
        mvc.perform(get("/api/course/courses/MATH101/access").with(httpBasic("student1", PASSWORD)))
                .andExpect(status().isForbidden());
        clock.set("2026-12-01T00:00:01Z");
        mvc.perform(get("/api/course/courses/MATH101/access").with(httpBasic("student1", PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void invalid_selfEnrolmentIsOffByDefault() throws Exception {
        mvc.perform(json(post("/api/course/courses/MATH101/self-enrolment"), "student1", "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ENROLMENT_INACTIVE"));
    }

    @Test
    void normal_selfEnrolmentWithKey() throws Exception {
        mvc.perform(json(put("/api/course/courses/MATH101/self-enrolment"), "teacher2",
                        "{\"enabled\":true,\"enrolmentKey\":\"secret\"}"))
                .andExpect(status().isOk());
        mvc.perform(json(post("/api/course/courses/MATH101/self-enrolment"), "student1", "{\"key\":\"wrong\"}"))
                .andExpect(jsonPath("$.error").value("WRONG_ENROLMENT_KEY"));
        mvc.perform(json(post("/api/course/courses/MATH101/self-enrolment"), "student1", "{\"key\":\"secret\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.method").value("SELF"));
    }

    @Test
    void invalid_prerequisiteRuleRefusesStudentEnrolment() throws Exception {
        // CS201 requires CS101 (REQ-ADV-09); student4 has not completed it. The rule is advising's, run by course.
        mvc.perform(json(post("/api/course/courses/CS201/enrolments"), "teacher1",
                        "{\"username\":\"student4\",\"role\":\"STUDENT\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PREREQUISITES_MISSING"));
    }

    @Test
    void normal_prerequisiteRuleDoesNotApplyToTeachers() throws Exception {
        mvc.perform(json(post("/api/course/courses/CS201/enrolments"), "admin",
                        "{\"username\":\"teacher2\",\"role\":\"TEACHER\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void normal_myCoursesListsActiveEnrolments() throws Exception {
        mvc.perform(get("/api/course/my-courses").with(httpBasic("student4", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.shortname == 'MATH101')]").exists());
    }
}
