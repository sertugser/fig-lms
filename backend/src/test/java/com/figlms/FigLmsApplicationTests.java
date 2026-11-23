package com.figlms;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Starts the whole application against an in-memory H2 database (profile "test"), so CI needs no PostgreSQL.
 */
@SpringBootTest(properties = "spring.profiles.active=test")
class FigLmsApplicationTests {

    @Test
    void contextLoads() {
    }
}
