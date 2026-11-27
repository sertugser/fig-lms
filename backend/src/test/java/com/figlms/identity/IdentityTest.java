package com.figlms.identity;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** REQ-IDN-01..05: login, session, lockout, password policy and account administration. */
@SpringBootTest(properties = "spring.profiles.active=test")
@AutoConfigureMockMvc
@Import(TestClock.Config.class)
@Transactional
class IdentityTest {

    static final String PASSWORD = "test-password";

    @Autowired
    MockMvc mvc;

    @Autowired
    TestClock clock;

    @BeforeEach
    void resetClock() {
        clock.reset();
    }

    private org.springframework.test.web.servlet.ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/identity/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    @Test
    void normal_loginOpensASessionOfTwoHours() throws Exception {
        MockHttpSession session = (MockHttpSession) login("student1", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("student1"))
                .andExpect(jsonPath("$.fullName").value("Ali Demir"))
                .andExpect(jsonPath("$.sessionTimeoutSeconds").value(7200))
                .andReturn().getRequest().getSession();

        mvc.perform(get("/api/identity/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("student1"));
    }

    @Test
    void invalid_wrongPasswordGivesTheMoodleMessage() throws Exception {
        login("student1", "wrong").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_LOGIN"))
                .andExpect(jsonPath("$.message").value("Invalid login, please try again"));
    }

    @Test
    void invalid_unknownUserGivesTheSameAnswer() throws Exception {
        login("nobody", PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_LOGIN"));
    }

    @Test
    void boundary_fifthFailureLocksTheAccountForFifteenMinutes() throws Exception {
        for (int i = 0; i < 4; i++) {
            login("student2", "wrong").andExpect(status().isUnauthorized());
        }
        login("student2", PASSWORD).andExpect(status().isOk()); // 4 failures: not locked, success resets the count

        for (int i = 0; i < 5; i++) {
            login("student2", "wrong").andExpect(status().isUnauthorized());
        }
        login("student2", PASSWORD).andExpect(status().isUnauthorized()); // locked, even with the right password

        clock.set(TestClock.DEFAULT.plusSeconds(15 * 60 + 1));
        login("student2", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void invalid_requestWithoutLoginGets401AsJson() throws Exception {
        mvc.perform(get("/api/identity/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"));
    }

    @Test
    void normal_adminCreatesAUser() throws Exception {
        mvc.perform(post("/api/identity/users").with(httpBasic("admin", PASSWORD)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"student6","password":"Str0ng!Password","firstname":"Deniz",
                                 "lastname":"Aydin","email":"student6@example.com"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("student6"));
        login("student6", "Str0ng!Password").andExpect(status().isOk());
    }

    @Test
    void invalid_passwordPolicyIsChecked() throws Exception {
        mvc.perform(post("/api/identity/users").with(httpBasic("admin", PASSWORD)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"student7","password":"short","firstname":"A","lastname":"B",
                                 "email":"b@example.com"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PASSWORD_POLICY"));
    }

    @Test
    void invalid_duplicateUsername() throws Exception {
        mvc.perform(post("/api/identity/users").with(httpBasic("admin", PASSWORD)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"student1","password":"Str0ng!Password","firstname":"A","lastname":"B",
                                 "email":"b@example.com"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("USERNAME_TAKEN"));
    }

    @Test
    void invalid_onlyTheAdminCreatesUsers() throws Exception {
        mvc.perform(post("/api/identity/users").with(httpBasic("teacher1", PASSWORD)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"password\":\"Str0ng!Password\",\"firstname\":\"A\",\"lastname\":\"B\",\"email\":\"x@example.com\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void normal_suspendedUserCannotLogIn() throws Exception {
        mvc.perform(put("/api/identity/users/student5/suspended").with(httpBasic("admin", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"suspended\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspended").value(true));
        login("student5", PASSWORD).andExpect(status().isUnauthorized());
    }
}
