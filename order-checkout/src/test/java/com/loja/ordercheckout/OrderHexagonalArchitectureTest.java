package com.loja.ordercheckout;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.INTERFACES;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architectural guard rails: enforces the hexagonal layout of the order-checkout
 * module (spec §0 and §13). Fails the build if the boundaries (domain / application
 * / adapter) are crossed. Mirrors {@code UserHexagonalArchitectureTest} and
 * {@code ProductHexagonalArchitectureTest}; the application layer may additionally
 * depend on the product-catalog ports it orchestrates.
 */
@AnalyzeClasses(packages = "com.loja.ordercheckout",
        importOptions = {ImportOption.DoNotIncludeTests.class})
public class OrderHexagonalArchitectureTest {

    private static final String[] EE_API = {"jakarta..", "javax.."};

    private static final String[] DOMAIN_ALLOWED_DEPENDENCIES = {
            "..ordercheckout.domain..",
            "..ordercheckout.application.dto..",
            "com.loja.shared..",
            "java..",
    };

    private static final String[] APPLICATION_ALLOWED_DEPENDENCIES = {
            "..ordercheckout.application..",
            "..ordercheckout.domain..",
            "com.loja.productcatalog..",
            "com.loja.shared..",
            "jakarta..",
            "java..",
    };

    @ArchTest
    static final ArchRule domain_should_not_depend_on_any_ee_api = noClasses()
            .that().resideInAPackage("..ordercheckout.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(EE_API);

    @ArchTest
    static final ArchRule domain_should_not_depend_on_adapters = noClasses()
            .that().resideInAPackage("..ordercheckout.domain..")
            .should().dependOnClassesThat().resideInAPackage("..ordercheckout.adapter..");

    @ArchTest
    static final ArchRule domain_should_only_depend_on_domain_shared_and_java = classes()
            .that().resideInAPackage("..ordercheckout.domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(DOMAIN_ALLOWED_DEPENDENCIES);

    @ArchTest
    static final ArchRule application_should_not_depend_on_adapters = noClasses()
            .that().resideInAPackage("..ordercheckout.application..")
            .should().dependOnClassesThat().resideInAPackage("..ordercheckout.adapter..");

    @ArchTest
    static final ArchRule application_should_only_depend_on_application_domain_shared_java_and_ee = classes()
            .that().resideInAPackage("..ordercheckout.application..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(APPLICATION_ALLOWED_DEPENDENCIES);

    @ArchTest
    static final ArchRule ports_should_be_interfaces = classes()
            .that().resideInAnyPackage("..ordercheckout.domain.port..")
            .should().beInterfaces();

    @ArchTest
    static final ArchRule adapters_should_implement_interfaces = classes()
            .that().resideInAPackage("..ordercheckout.adapter..")
            .and().haveSimpleNameEndingWith("Adapter")
            .should().implement(INTERFACES);

    @ArchTest
    static final ArchRule jpa_entities_only_used_in_persistence_adapter = noClasses()
            .that().resideOutsideOfPackage("..ordercheckout.adapter.out.persistence..")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("JpaEntity");
}
