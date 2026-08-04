package com.loja.admindashboard;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architectural guard rails for the admin-dashboard module (spec §0 and backlog S27).
 * The module composes use cases from the business modules — it must never reach into
 * any adapter layer, and its own layers must stay in their hexagonal homes. Mirrors
 * {@code OrderHexagonalArchitectureTest} / {@code ProductHexagonalArchitectureTest} /
 * {@code UserHexagonalArchitectureTest}.
 */
@AnalyzeClasses(packages = "com.loja.admindashboard",
        importOptions = {ImportOption.DoNotIncludeTests.class})
public class AdminDashboardHexagonalArchitectureTest {

    private static final String[] EE_API = {"jakarta..", "javax.."};

    @ArchTest
    static final ArchRule domain_ports_should_be_interfaces = classes()
            .that().resideInAPackage("..admindashboard.domain.port..")
            .should().beInterfaces();

    @ArchTest
    static final ArchRule domain_should_not_depend_on_any_ee_api = noClasses()
            .that().resideInAPackage("..admindashboard.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(EE_API);

    @ArchTest
    static final ArchRule nothing_should_depend_on_a_top_level_adapter_class = noClasses()
            .that().resideInAPackage("..admindashboard..")
            .should().dependOnClassesThat(resideInAPackage("..adapter..")
                    .and(JavaClass.Predicates.TOP_LEVEL_CLASSES));

    @ArchTest
    static final ArchRule application_should_only_consume_own_and_cross_module_domain_application_shared = classes()
            .that().resideInAPackage("..admindashboard.application..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "..admindashboard..",
                    "..domain..",
                    "..application..",
                    "com.loja.shared..",
                    "jakarta..",
                    "java..");

    @ArchTest
    static final ArchRule services_should_implement_input_ports = classes()
            .that().resideInAPackage("..admindashboard.application.service..")
            .should().implement(resideInAPackage("..admindashboard.domain.port.in.."));

    @ArchTest
    static final ArchRule beans_should_reside_in_adapter_in_web = classes()
            .that().haveSimpleNameEndingWith("Bean")
            .should().resideInAPackage("..admindashboard.adapter.in.web..");

    @ArchTest
    static final ArchRule dtos_should_be_final = classes()
            .that().resideInAPackage("..admindashboard.application.dto..")
            .should().haveModifier(JavaModifier.FINAL);
}
