package com.figlms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Fig LMS modular monolith. Each direct subpackage is one application module (see ADR-0001).
 */
@SpringBootApplication
public class FigLmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(FigLmsApplication.class, args);
    }
}
