package com.figlms.course;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * REQ-CRS-10 (MDL-85503): with course browsing turned off, Moodle 5.3.1 shows a student nothing; here the student sees
 * the categories and courses they are enrolled in.
 */
@SpringBootTest(properties = {"spring.profiles.active=test", "fig.course.browse-all-courses=false"})
@AutoConfigureMockMvc
class BrowseOwnCoursesTest {

    @Autowired
    MockMvc mvc;

    @Test
    void normal_studentSeesOnlyTheirCourses() throws Exception {
        mvc.perform(get("/api/course/categories").with(httpBasic("student4", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courses.length()").value(2))
                .andExpect(jsonPath("$[0].courses[?(@.shortname == 'CS201')]").doesNotExist());
    }

    @Test
    void normal_adminStillSeesEverything() throws Exception {
        mvc.perform(get("/api/course/categories").with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courses.length()").value(3));
    }
}
