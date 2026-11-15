package dev.cloudpilot;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * ADR-0001 as executable rules: the modular monolith only stays modular if the build fails the
 * moment someone reaches into another module's internals.
 *
 * Plain JUnit tests around ArchUnit (not ArchUnit's own JUnit engine): with Surefire's -Dtest
 * filter the engine silently ran zero rules, and a check that doesn't run is worse than none.
 */
class ArchitectureTest {

    private static JavaClasses main;

    @BeforeAll
    static void importProductionClasses() {
        main = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("dev.cloudpilot");
    }

    @Test
    void modulesHaveNoCycles() {
        slices().matching("dev.cloudpilot.(*)..").should().beFreeOfCycles().check(main);
    }

    /** A module's tables are its own: other modules go through its public services. */
    @Test
    void repositoriesAreOnlyUsedInsideTheirModule() {
        classes().that().areAssignableTo(Repository.class)
                .should(ModuleRules.onlyBeAccessedFromTheirOwnModule())
                .check(main);
    }

    /** JPA entities never leave their module; other modules get records (views, refs, events). */
    @Test
    void entitiesAreOnlyUsedInsideTheirModule() {
        classes().that().areAnnotatedWith(Entity.class)
                .should(ModuleRules.onlyBeAccessedFromTheirOwnModule())
                .check(main);
    }

    /** Controllers are entry points; no other code may call them. */
    @Test
    void nothingDependsOnControllers() {
        noClasses().should().dependOnClassesThat().areAnnotatedWith(RestController.class)
                .allowEmptyShould(true)
                .check(main);
    }

    /** ADR-0002: the AI module has no way to run processes. */
    @Test
    void aiModuleCannotExecuteCommands() {
        noClasses().that().resideInAPackage("dev.cloudpilot.ai..")
                .should().dependOnClassesThat().belongToAnyOf(ProcessBuilder.class, Runtime.class)
                .check(main);
    }
}
