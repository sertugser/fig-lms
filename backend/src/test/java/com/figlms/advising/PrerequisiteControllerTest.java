package com.figlms.advising;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The four golden master scenarios of REQ-ADV-09 through {@code GET /api/advising/prerequisite-check}.
 * Users and password come from the test profile (identity SecurityConfig).
 */
@SpringBootTest(properties = "spring.profiles.active=test")
@AutoConfigureMockMvc
class PrerequisiteControllerTest {

    static final String PASSWORD = "test-password";
    static final String URL = "/api/advising/prerequisite-check";

    @Autowired
    MockMvc mvc;

    @Test
    void normal_allowed() throws Exception {
        mvc.perform(get(URL).param("student", "student1").param("course", "CS201").with(httpBasic("student1", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"student":"student1","course":"CS201","allowed":true,
                         "reason":"PREREQUISITES_COMPLETED","missingPrerequisites":[]}""", true));
    }

    @Test
    void normal_notAllowed() throws Exception {
        mvc.perform(get(URL).param("student", "student3").param("course", "CS201").with(httpBasic("student3", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"student":"student3","course":"CS201","allowed":false,
                         "reason":"PREREQUISITES_MISSING","missingPrerequisites":["CS101"]}""", true));
    }

    @Test
    void boundary_courseWithoutPrerequisites() throws Exception {
        mvc.perform(get(URL).param("student", "student5").param("course", "CS101").with(httpBasic("student5", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.reason").value("NO_PREREQUISITES"));
    }

    @Test
    void invalid_missingParameter() throws Exception {
        mvc.perform(get(URL).param("student", "student1").with(httpBasic("student1", PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void invalid_blankParameter() throws Exception {
        mvc.perform(get(URL).param("student", "student1").param("course", " ").with(httpBasic("student1", PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void invalid_unknownCourse() throws Exception {
        mvc.perform(get(URL).param("student", "student1").param("course", "CS999").with(httpBasic("student1", PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("UNKNOWN_COURSE"));
    }

    @Test
    void invalid_unknownStudent() throws Exception {
        mvc.perform(get(URL).param("student", "nobody").param("course", "CS201").with(httpBasic("teacher1", PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("UNKNOWN_STUDENT"));
    }

    @Test
    void unauthorized_noLogin() throws Exception {
        mvc.perform(get(URL).param("student", "student1").param("course", "CS201"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthorized_wrongPassword() throws Exception {
        mvc.perform(get(URL).param("student", "student1").param("course", "CS201").with(httpBasic("student1", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthorized_studentAsksAboutAnotherStudent() throws Exception {
        mvc.perform(get(URL).param("student", "student1").param("course", "CS201").with(httpBasic("student3", PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherMayCheckAnyStudent() throws Exception {
        mvc.perform(get(URL).param("student", "student3").param("course", "CS201").with(httpBasic("teacher1", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false));
    }

    @Test
    void healthStaysPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
