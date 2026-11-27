package com.figlms.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.figlms.TestClock;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * REQ-ASG-01..08 end to end, with the gradebook, completion and file modules behind them. A new assignment in CS101
 * opens 2026-11-01, is due 2026-11-15 23:59 and cuts off 2026-11-22 23:59 unless a test says otherwise.
 */
@SpringBootTest(properties = "spring.profiles.active=test")
@AutoConfigureMockMvc
@Import(TestClock.Config.class)
@Transactional
class AssignmentFlowTest {

    static final String PASSWORD = "test-password";
    static final String FROM = "2026-11-01T00:00:00Z";
    static final String DUE = "2026-11-15T23:59:00Z";
    static final String CUTOFF = "2026-11-22T23:59:00Z";

    @Autowired
    MockMvc mvc;

    @Autowired
    TestClock clock;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void beforeTheDueDate() {
        clock.set("2026-11-10T12:00:00Z");
    }

    // ---- helpers

    long create(String extraSettings) throws Exception {
        String body = "{\"name\":\"Lab 1\",\"allowSubmissionsFrom\":\"" + FROM + "\",\"dueDate\":\"" + DUE
                + "\",\"cutoffDate\":\"" + CUTOFF + "\"" + (extraSettings.isEmpty() ? "" : "," + extraSettings) + "}";
        String response = mvc.perform(post("/api/assignment/courses/CS101/assignments").with(httpBasic("teacher1", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    MockMultipartHttpServletRequestBuilder save(long id, String user) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/assignment/assignments/{id}/submission", id);
        request.with(httpBasic(user, PASSWORD));
        return request;
    }

    static MockMultipartFile file(String name) {
        return new MockMultipartFile("files", name, "application/pdf", ("content of " + name).getBytes(StandardCharsets.UTF_8));
    }

    ResultActions grade(long id, String student, String body) throws Exception {
        return mvc.perform(put("/api/assignment/assignments/{id}/submissions/{u}/grade", id, student)
                .with(httpBasic("teacher1", PASSWORD)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    ResultActions submissionOf(long id, String user) throws Exception {
        return mvc.perform(get("/api/assignment/assignments/{id}/submission", id).with(httpBasic(user, PASSWORD)));
    }

    JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    long seededAssignment() {
        return jdbc.sql("SELECT id FROM asg_assignment WHERE name = 'Assignment 1'").query(Long.class).single();
    }

    // ---- REQ-ASG-01 / 02: window and lateness

    @Test
    void normal_saveWithoutDraftsIsSubmittedAtOnce() throws Exception {
        long id = create("\"onlineTextEnabled\":true");
        mvc.perform(save(id, "student1").param("onlineText", "My answer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.attemptNumber").value(0))
                .andExpect(jsonPath("$.late").value(false));
    }

    @Test
    void boundary_atTheDueDateIsOnTimeOneSecondLaterIsLate() throws Exception {
        long id = create("\"onlineTextEnabled\":true");
        clock.set(DUE);
        mvc.perform(save(id, "student1").param("onlineText", "on time")).andExpect(jsonPath("$.late").value(false));
        clock.set("2026-11-15T23:59:01Z");
        mvc.perform(save(id, "student2").param("onlineText", "late")).andExpect(jsonPath("$.late").value(true));
    }

    @Test
    void boundary_windowOpensAtTheOpeningDateAndClosesAfterTheCutoff() throws Exception {
        long id = create("\"onlineTextEnabled\":true");
        clock.set("2026-10-31T23:59:59Z");
        mvc.perform(save(id, "student1").param("onlineText", "x"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("NOT_YET_OPEN"));
        clock.set(CUTOFF);
        mvc.perform(save(id, "student1").param("onlineText", "x")).andExpect(status().isOk());
        clock.set("2026-11-22T23:59:01Z");
        mvc.perform(save(id, "student2").param("onlineText", "x"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("SUBMISSIONS_CLOSED"));
    }

    @Test
    void boundary_laterExtensionReopensTheWindow() throws Exception {
        long id = create("\"onlineTextEnabled\":true");
        mvc.perform(put("/api/assignment/assignments/{id}/extensions/student2", id).with(httpBasic("teacher1", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"extensionDueDate\":\"2026-11-25T12:00:00Z\"}"))
                .andExpect(status().isNoContent());
        clock.set("2026-11-24T00:00:00Z");
        mvc.perform(save(id, "student2").param("onlineText", "x"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.late").value(false));
        mvc.perform(save(id, "student3").param("onlineText", "x")).andExpect(status().isConflict());
    }

    @Test
    void invalid_extensionMustBeAfterTheDueDate() throws Exception {
        long id = create("");
        mvc.perform(put("/api/assignment/assignments/{id}/extensions/student2", id).with(httpBasic("teacher1", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"extensionDueDate\":\"" + DUE + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("EXTENSION_NOT_AFTER_DUE_DATE"));
    }

    @Test
    void invalid_dueDateMustBeAfterTheOpeningDate() throws Exception {
        mvc.perform(post("/api/assignment/courses/CS101/assignments").with(httpBasic("teacher1", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"allowSubmissionsFrom\":\"" + DUE + "\",\"dueDate\":\"" + DUE + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("DUE_DATE_BEFORE_FROM_DATE"));
        mvc.perform(post("/api/assignment/courses/CS101/assignments").with(httpBasic("teacher1", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"dueDate\":\"" + DUE + "\",\"cutoffDate\":\"" + FROM + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CUTOFF_BEFORE_DUE_DATE"));
    }

    @Test
    void invalid_onlyEditingTeachersCreateAssignments() throws Exception {
        mvc.perform(post("/api/assignment/courses/CS101/assignments").with(httpBasic("student1", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    // ---- REQ-ASG-03: eligibility and locks

    @Test
    void invalid_teacherCannotSubmit() throws Exception {
        long id = create("\"onlineTextEnabled\":true");
        mvc.perform(save(id, "teacher1").param("onlineText", "x"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("NO_SUBMIT_PERMISSION"));
    }

    @Test
    void invalid_notEnrolledStudentCannotOpenTheAssignment() throws Exception {
        long id = create("\"onlineTextEnabled\":true");
        submissionOf(id, "student4").andExpect(status().isOk());
        mvc.perform(save(id, "admin").param("onlineText", "x")).andExpect(status().isForbidden());
        mvc.perform(get("/api/assignment/assignments/{id}/submission", id).with(httpBasic("teacher2", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("NOT_ENROLLED"));
    }

    @Test
    void normal_lockedSubmissionCannotBeChanged() throws Exception {
        long id = create("\"onlineTextEnabled\":true");
        mvc.perform(put("/api/assignment/assignments/{id}/locks/student1", id).with(httpBasic("teacher1", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"locked\":true}"))
                .andExpect(status().isNoContent());
        mvc.perform(save(id, "student1").param("onlineText", "x"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SUBMISSION_LOCKED"));
        submissionOf(id, "student1").andExpect(jsonPath("$.canEdit").value(false))
                .andExpect(jsonPath("$.editRefusal.code").value("SUBMISSION_LOCKED"));
    }

    @Test
    void normal_lockedGradebookGradeBlocksEditing() throws Exception {
        long id = create("\"onlineTextEnabled\":true");
        long item = body(mvc.perform(get("/api/assignment/assignments/{id}", id).with(httpBasic("teacher1", PASSWORD))))
                .get("gradeItemId").asLong();
        mvc.perform(put("/api/grading/items/{item}/grades/student1/flags", item).with(httpBasic("teacher1", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"hidden\":false,\"locked\":true}"))
                .andExpect(status().isNoContent());
        mvc.perform(save(id, "student1").param("onlineText", "x"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("GRADE_LOCKED"));
    }

    // ---- REQ-ASG-04: drafts and the submission statement

    @Test
    void normal_draftIsSubmittedOnlyAfterAcceptingTheStatement() throws Exception {
        long id = create("\"onlineTextEnabled\":true,\"submissionDrafts\":true,\"requireStatement\":true");
        mvc.perform(save(id, "student1").param("onlineText", "draft"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.canSubmit").value(true))
                .andExpect(jsonPath("$.statement").value(containsString("my own work")));
        mvc.perform(post("/api/assignment/assignments/{id}/submission/submit", id).with(httpBasic("student1", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"acceptStatement\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("STATEMENT_REQUIRED"));
        mvc.perform(post("/api/assignment/assignments/{id}/submission/submit", id).with(httpBasic("student1", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"acceptStatement\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.canEdit").value(false));
        mvc.perform(save(id, "student1").param("onlineText", "change"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ALREADY_SUBMITTED"));
    }

    @Test
    void invalid_emptySaveIsRejected() throws Exception {
        long id = create("\"onlineTextEnabled\":true");
        mvc.perform(save(id, "student1").param("onlineText", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SUBMISSION_EMPTY"))
                .andExpect(jsonPath("$.message").value("Nothing was submitted"));
    }

    // ---- REQ-ASG-05 and REQ-FIL-*: files

    @Test
    void normal_fileIsStoredOnTheAttemptAndTheLimitIsKept() throws Exception {
        long id = create("\"maxFiles\":1,\"acceptedTypes\":[\".pdf\"]");
        JsonNode saved = body(mvc.perform(save(id, "student1").file(file("report.pdf"))).andExpect(status().isOk()));
        assertThat(saved.get("files")).hasSize(1);
        assertThat(saved.get("files").get(0).get("filename").asText()).isEqualTo("report.pdf");

        mvc.perform(save(id, "student1").file(file("second.pdf")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("TOO_MANY_FILES"));
        mvc.perform(save(id, "student2").file(new MockMultipartFile("files", "virus.exe", "application/octet-stream", new byte[] {1})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("FILE_TYPE_NOT_ACCEPTED"));
        // Replacing the one file is allowed.
        long fileId = saved.get("files").get(0).get("id").asLong();
        mvc.perform(save(id, "student1").file(file("final.pdf")).param("removeFileIds", String.valueOf(fileId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files.length()").value(1))
                .andExpect(jsonPath("$.files[0].filename").value("final.pdf"));
    }

    @Test
    void invalid_fileOnlySaveWithoutFilesIsRejected() throws Exception {
        long id = create("\"maxFiles\":1");
        mvc.perform(save(id, "student1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SUBMISSION_EMPTY"));
    }

    @Test
    void normal_onlyTheOwnerAndGradersDownloadASubmissionFile() throws Exception {
        long id = create("\"maxFiles\":2");
        JsonNode saved = body(mvc.perform(save(id, "student1").file(file("../secret<1>.pdf"))));
        String url = saved.get("files").get(0).get("url").asText();
        assertThat(saved.get("files").get(0).get("filename").asText()).isEqualTo("..secret1.pdf");

        mvc.perform(get(url).with(httpBasic("student1", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")));
        mvc.perform(get(url).with(httpBasic("teacher1", PASSWORD))).andExpect(status().isOk());
        mvc.perform(get(url).with(httpBasic("student2", PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("FILE_NOT_FOUND"));
        mvc.perform(get(url).with(httpBasic("teacher2", PASSWORD))).andExpect(status().isNotFound());
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/files/999999").with(httpBasic("student1", PASSWORD))).andExpect(status().isNotFound());
    }

    // ---- REQ-ASG-06 / 07: attempts and reopening

    @Test
    void normal_manualReopenGrantsAttemptsUpToTheLimit() throws Exception {
        long id = create("\"onlineTextEnabled\":true,\"reopenMethod\":\"MANUAL\",\"maxAttempts\":2");
        mvc.perform(save(id, "student1").param("onlineText", "v1"));
        grade(id, "student1", "{\"grade\":40}").andExpect(jsonPath("$.latestAttempt").value(0))
                .andExpect(jsonPath("$.attemptAdded").value(false));
        grade(id, "student1", "{\"grade\":40,\"addAttempt\":true}").andExpect(jsonPath("$.latestAttempt").value(1));
        submissionOf(id, "student1").andExpect(jsonPath("$.status").value("REOPENED"))
                .andExpect(jsonPath("$.attemptNumber").value(1))
                .andExpect(jsonPath("$.history[0].grade").value(40.0));
        mvc.perform(post("/api/assignment/assignments/{id}/submissions/student1/attempts", id).with(httpBasic("teacher1", PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("MAX_ATTEMPTS_REACHED"));
    }

    @Test
    void invalid_directAttemptNeedsASubmissionAndTheGradeCapability() throws Exception {
        long id = create("\"onlineTextEnabled\":true,\"reopenMethod\":\"MANUAL\",\"maxAttempts\":-1");
        mvc.perform(post("/api/assignment/assignments/{id}/submissions/student2/attempts", id).with(httpBasic("teacher1", PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NO_SUBMISSION"));
        mvc.perform(save(id, "student2").param("onlineText", "v1"));
        mvc.perform(post("/api/assignment/assignments/{id}/submissions/student2/attempts", id).with(httpBasic("student2", PASSWORD)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/assignment/assignments/{id}/submissions/student2/attempts", id).with(httpBasic("teacher1", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestAttempt").value(1));
    }

    @Test
    void boundary_gradingFormCreatesAttemptZeroForAStudentWithoutSubmission() throws Exception {
        long id = create("\"reopenMethod\":\"MANUAL\",\"maxAttempts\":2");
        grade(id, "student5", "{\"grade\":10,\"addAttempt\":true}")
                .andExpect(jsonPath("$.gradedAttempt").value(0))
                .andExpect(jsonPath("$.latestAttempt").value(1));
    }

    @Test
    void normal_untilPassReopensOnlyAFailingGrade() throws Exception {
        long id = create("\"onlineTextEnabled\":true,\"gradePass\":50,\"maxAttempts\":3");
        mvc.perform(save(id, "student1").param("onlineText", "v1"));
        grade(id, "student1", "{\"grade\":49.99}").andExpect(jsonPath("$.latestAttempt").value(1));
        grade(id, "student1", "{\"grade\":50}").andExpect(jsonPath("$.latestAttempt").value(1));
    }

    @Test
    void boundary_untilPassWithoutAGradeToPassNeverReopens() throws Exception {
        long id = create("\"onlineTextEnabled\":true,\"maxAttempts\":3");
        mvc.perform(save(id, "student1").param("onlineText", "v1"));
        grade(id, "student1", "{\"grade\":1}").andExpect(jsonPath("$.latestAttempt").value(0));
    }

    @Test
    void normal_gradingAnOlderAttemptDoesNotReopen() throws Exception {
        long id = create("\"onlineTextEnabled\":true,\"reopenMethod\":\"AUTOMATIC\",\"maxAttempts\":-1");
        mvc.perform(save(id, "student1").param("onlineText", "v1"));
        grade(id, "student1", "{\"grade\":20}").andExpect(jsonPath("$.latestAttempt").value(1));
        grade(id, "student1", "{\"grade\":25,\"attemptNumber\":0}")
                .andExpect(jsonPath("$.gradedAttempt").value(0))
                .andExpect(jsonPath("$.latestAttempt").value(1));
    }

    @Test
    void invalid_gradeOutsideTheRange() throws Exception {
        long id = create("\"onlineTextEnabled\":true");
        grade(id, "student1", "{\"grade\":100.5}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("GRADE_ABOVE_MAXIMUM"));
        grade(id, "student1", "{\"grade\":-1}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("GRADE_BELOW_ZERO"));
    }

    // ---- REQ-ASG-08: MDL-87490

    @Test
    void bugfix_revertToDraftKeepsTheSameAttemptAndItsFile() throws Exception {
        long id = create("\"maxFiles\":1,\"submissionDrafts\":true,\"requireStatement\":true,"
                + "\"reopenMethod\":\"AUTOMATIC\",\"maxAttempts\":-1");
        mvc.perform(save(id, "student1").file(file("work.pdf"))).andExpect(jsonPath("$.status").value("DRAFT"));
        mvc.perform(post("/api/assignment/assignments/{id}/submission/submit", id).with(httpBasic("student1", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"acceptStatement\":true}"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        mvc.perform(post("/api/assignment/assignments/{id}/submissions/student1/revert-to-draft", id)
                        .with(httpBasic("teacher1", PASSWORD)))
                .andExpect(status().isNoContent());

        submissionOf(id, "student1")
                .andExpect(jsonPath("$.attemptNumber").value(0))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.files[0].filename").value("work.pdf"))
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.history.length()").value(0));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM asg_submission WHERE assignment_id = ?").param(id).query(Long.class).single())
                .isEqualTo(1);

        // The student replaces the file and submits the same attempt again.
        long fileId = body(submissionOf(id, "student1")).get("files").get(0).get("id").asLong();
        mvc.perform(save(id, "student1").file(file("work-v2.pdf")).param("removeFileIds", String.valueOf(fileId)))
                .andExpect(jsonPath("$.attemptNumber").value(0));
        mvc.perform(post("/api/assignment/assignments/{id}/submission/submit", id).with(httpBasic("student1", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"acceptStatement\":true}"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.attemptNumber").value(0));

        // Regression control: grading still opens the next attempt under AUTOMATIC.
        grade(id, "student1", "{\"grade\":60}").andExpect(jsonPath("$.latestAttempt").value(1));
    }

    @Test
    void invalid_revertNeedsASubmittedAttempt() throws Exception {
        long id = create("\"onlineTextEnabled\":true,\"submissionDrafts\":true");
        mvc.perform(post("/api/assignment/assignments/{id}/submissions/student1/revert-to-draft", id)
                        .with(httpBasic("teacher1", PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NOT_SUBMITTED"));
    }

    // ---- integration: gradebook, completion, prerequisites, availability

    @Test
    void normal_gradeReachesTheGradebookAndCompletesTheCourseForThePrerequisite() throws Exception {
        clock.reset();
        long id = seededAssignment();
        mvc.perform(get("/api/advising/prerequisite-check").param("student", "student4").param("course", "CS201")
                        .with(httpBasic("student4", PASSWORD)))
                .andExpect(jsonPath("$.allowed").value(false));

        grade(id, "student4", "{\"grade\":90}").andExpect(status().isOk());

        mvc.perform(get("/api/grading/courses/CS101/users/student4").with(httpBasic("student4", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.name == 'Assignment 1')].grade").value(90.0));
        mvc.perform(get("/api/advising/courses/CS101/completion/student4").with(httpBasic("student4", PASSWORD)))
                .andExpect(jsonPath("$.complete").value(true));
        mvc.perform(get("/api/advising/prerequisite-check").param("student", "student4").param("course", "CS201")
                        .with(httpBasic("student4", PASSWORD)))
                .andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    void normal_failingGradeDoesNotCompleteTheCourse() throws Exception {
        clock.reset();
        grade(seededAssignment(), "student4", "{\"grade\":30}").andExpect(status().isOk());
        mvc.perform(get("/api/advising/courses/CS101/completion/student4").with(httpBasic("student4", PASSWORD)))
                .andExpect(jsonPath("$.complete").value(false));
    }

    @Test
    void normal_restrictionOnAnotherActivity() throws Exception {
        long first = seededAssignment();
        String restriction = "{\\\"op\\\":\\\"&\\\",\\\"showc\\\":[true],\\\"c\\\":[{\\\"type\\\":\\\"completion\\\",\\\"cm\\\":\\\"assignment:"
                + first + "\\\",\\\"e\\\":1}]}";
        long id = create("\"onlineTextEnabled\":true,\"availability\":\"" + restriction + "\"");

        submissionOf(id, "student4").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("NOT_AVAILABLE"))
                .andExpect(jsonPath("$.message").value(containsString("Assignment 1")));
        submissionOf(id, "student1").andExpect(status().isOk()); // complete and passed
        submissionOf(id, "student3").andExpect(status().isForbidden()); // complete but failed is not "complete"
        mvc.perform(get("/api/assignment/assignments/{id}", id).with(httpBasic("teacher1", PASSWORD)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/assignment/courses/CS101/assignments").with(httpBasic("student4", PASSWORD)))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].available").value(false));
    }

    @Test
    void invalid_malformedRestrictionIsRejected() throws Exception {
        mvc.perform(post("/api/assignment/courses/CS101/assignments").with(httpBasic("teacher1", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\",\"availability\":\"{not json\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_AVAILABILITY"));
    }

    @Test
    void normal_gradingTableListsEveryStudent() throws Exception {
        clock.reset();
        mvc.perform(get("/api/assignment/assignments/{id}/submissions", seededAssignment()).with(httpBasic("teacher1", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[?(@.username == 'student1')].grade").value(85.0))
                .andExpect(jsonPath("$[?(@.username == 'student4')].late").value(true))
                .andExpect(jsonPath("$[?(@.username == 'student5')].status").value("NEW"));
        mvc.perform(get("/api/assignment/assignments/{id}/submissions", seededAssignment()).with(httpBasic("student1", PASSWORD)))
                .andExpect(status().isForbidden());
    }
}
