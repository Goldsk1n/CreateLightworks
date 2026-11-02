package com.goldskinmc.lightworks;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Enforces the dependency rules in the workspace CLAUDE.md, so breaking one fails the build.
 */
class ArchitectureTest {
    private static JavaClasses mod;

    @BeforeAll
    static void importModClasses() {
        mod = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.goldskinmc.lightworks");
    }

    @Test
    void importSeesOptionalModDependencies() {
        // Guards against the rules passing vacuously: optional mods are not on the test classpath,
        // so their types must still show up as dependencies read from our bytecode
        boolean seesLdl = mod.get("com.goldskinmc.lightworks.compat.ldl.LightworksDynamicLights")
                .getDirectDependenciesFromSelf().stream()
                .anyMatch(d -> d.getTargetClass().getPackageName().startsWith("dev.lambdaurora"));
        org.junit.jupiter.api.Assertions.assertTrue(seesLdl, "LambDynamicLights dependency not visible to ArchUnit");
    }

    @Test
    void neverReferencesCreateAeronautics() {
        // Aeronautics is coupled through redstone only; its maintainers ask that its code is not used
        noClasses().should().dependOnClassesThat().resideInAnyPackage("dev.eriksonn..", "dev.simulated_team..")
                .because("Create Aeronautics is integrated through redstone only")
                .check(mod);
    }

    @Test
    void usesSableOnlyThroughCompanion() {
        noClasses().should().dependOnClassesThat(
                        resideInAPackage("dev.ryanhcode.sable..")
                                .and(not(resideInAPackage("dev.ryanhcode.sable.companion.."))))
                .because("Sable is optional and only Sable Companion is safe to call without it")
                .check(mod);
    }

    @Test
    void usesLambDynamicLightsOnlyInItsCompatPackage() {
        noClasses().that().resideOutsideOfPackage("com.goldskinmc.lightworks.compat.ldl..")
                .should().dependOnClassesThat().resideInAPackage("dev.lambdaurora..")
                .because("LambDynamicLights is optional and client-only")
                .check(mod);
    }

    @Test
    void usesSodiumDynamicLightsOnlyInItsCompatPackage() {
        noClasses().that().resideOutsideOfPackage("com.goldskinmc.lightworks.compat.sdl..")
                .should().dependOnClassesThat().resideInAPackage("toni.sodiumdynamiclights..")
                .because("Sodium Dynamic Lights is optional and client-only")
                .check(mod);
    }

    @Test
    void reachesModSpecificCompatOnlyFromCompat() {
        // Code outside compat/ goes through a guard in compat/ that checks the other mod is loaded
        noClasses().that().resideOutsideOfPackage("com.goldskinmc.lightworks.compat..")
                .should().dependOnClassesThat().resideInAPackage("com.goldskinmc.lightworks.compat.*..")
                .because("classes for an optional mod must only load when that mod is present")
                .check(mod);
    }

    @Test
    void beamMathsStaysFreeOfMinecraft() {
        classes().that().resideInAPackage("com.goldskinmc.lightworks.beam..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "com.goldskinmc.lightworks.beam..", "org.jetbrains.annotations..")
                .because("beam maths is unit-tested without booting the game")
                .allowEmptyShould(true)
                .check(mod);
    }
}
