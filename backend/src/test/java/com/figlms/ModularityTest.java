package com.figlms;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Fails when a module uses another module's internal classes or when modules depend on each other in a cycle.
 */
class ModularityTest {

    static final ApplicationModules MODULES = ApplicationModules.of(FigLmsApplication.class);

    @Test
    void verifiesModuleBoundaries() {
        MODULES.verify();
    }

    @Test
    void hasTheSixModulesFromAdr0001() {
        org.assertj.core.api.Assertions.assertThat(MODULES.stream().map(m -> m.getIdentifier().toString()))
                .containsExactlyInAnyOrder("identity", "course", "advising", "assignment", "grading", "planning");
    }
}
