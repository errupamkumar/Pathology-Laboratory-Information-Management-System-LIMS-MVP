package com.lims.server;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Architecture fitness test. Fails the build if a module reaches into another module's internals
 * (anything outside its base package or {@code @NamedInterface} packages) or if modules form a
 * dependency cycle. lims-common is an OPEN shared kernel.
 */
class ModularityTest {

    static final ApplicationModules MODULES = ApplicationModules.of("com.lims");

    @Test
    void moduleBoundariesAreRespected() {
        MODULES.verify();
    }

    @Test
    void writeModuleDocumentation() {
        // Generates PlantUML component diagrams + module canvases into target/spring-modulith-docs
        new Documenter(MODULES)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml()
                .writeModuleCanvases();
    }
}
