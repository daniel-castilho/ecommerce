package com.loja.useraccount;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.INTERFACES;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architectural guard rails: enforces the hexagonal layout of the module.
 * Fails the build if the boundaries (domain / application / adapter) are crossed.
 */
@AnalyzeClasses(packages = "com.loja.useraccount",
        importOptions = {ImportOption.DoNotIncludeTests.class})
public class UserHexagonalArchitectureTest {

    private static final String[] EE_API = {"jakarta..", "javax.."};

    private static final String[] DOMAIN_ALLOWED_DEPENDENCIES = {
            "..useraccount.domain..",
            "..useraccount.application.dto..",
            "com.loja.shared..",
            "java..",
    };

    private static final String[] APPLICATION_ALLOWED_DEPENDENCIES = {
            "..useraccount.application..",
            "..useraccount.domain..",
            "com.loja.shared..",
            "jakarta..",
            "java..",
    };

    @ArchTest
    static final ArchRule domain_should_not_depend_on_any_ee_api = noClasses()
            .that().resideInAPackage("..useraccount.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(EE_API);

    @ArchTest
    static final ArchRule domain_should_not_depend_on_adapters = noClasses()
            .that().resideInAPackage("..useraccount.domain..")
            .should().dependOnClassesThat().resideInAPackage("..useraccount.adapter..");

    @ArchTest
    static final ArchRule domain_should_only_depend_on_domain_shared_and_java = classes()
            .that().resideInAPackage("..useraccount.domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(DOMAIN_ALLOWED_DEPENDENCIES);

    @ArchTest
    static final ArchRule application_should_not_depend_on_adapters = noClasses()
            .that().resideInAPackage("..useraccount.application..")
            .should().dependOnClassesThat().resideInAPackage("..useraccount.adapter..");

    @ArchTest
    static final ArchRule application_should_only_depend_on_application_domain_shared_java_and_ee = classes()
            .that().resideInAPackage("..useraccount.application..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(APPLICATION_ALLOWED_DEPENDENCIES);

    @ArchTest
    static final ArchRule ports_should_be_interfaces = classes()
            .that().resideInAnyPackage("..useraccount.domain.port..")
            .should().beInterfaces();

    @ArchTest
    static final ArchRule adapters_should_implement_interfaces = classes()
            .that().resideInAPackage("..useraccount.adapter..")
            .and().haveSimpleNameEndingWith("Adapter")
            .should().implement(INTERFACES);
}
