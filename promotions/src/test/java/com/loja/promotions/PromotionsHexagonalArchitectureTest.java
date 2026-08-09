package com.loja.promotions;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.INTERFACES;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architectural guard rails: enforces the hexagonal layout of the promotions
 * module. Fails the build if the boundaries (domain / application / adapter)
 * are crossed. Mirrors {@code WishlistHexagonalArchitectureTest}.
 */
@AnalyzeClasses(packages = "com.loja.promotions",
        importOptions = {ImportOption.DoNotIncludeTests.class})
public class PromotionsHexagonalArchitectureTest {

    private static final String[] EE_API = {"jakarta..", "javax.."};

    private static final String[] DOMAIN_ALLOWED_DEPENDENCIES = {
            "..promotions.domain..",
            "..promotions.application.dto..",
            "com.loja.shared..",
            "java..",
    };

    private static final String[] APPLICATION_ALLOWED_DEPENDENCIES = {
            "..promotions.application..",
            "..promotions.domain..",
            "com.loja.shared..",
            "jakarta..",
            "java..",
    };

    @ArchTest
    static final ArchRule domain_should_not_depend_on_any_ee_api = noClasses()
            .that().resideInAPackage("..promotions.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(EE_API);

    @ArchTest
    static final ArchRule domain_should_not_depend_on_adapters = noClasses()
            .that().resideInAPackage("..promotions.domain..")
            .should().dependOnClassesThat().resideInAPackage("..promotions.adapter..");

    @ArchTest
    static final ArchRule domain_should_only_depend_on_domain_shared_and_java = classes()
            .that().resideInAPackage("..promotions.domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(DOMAIN_ALLOWED_DEPENDENCIES);

    @ArchTest
    static final ArchRule application_should_not_depend_on_adapters = noClasses()
            .that().resideInAPackage("..promotions.application..")
            .should().dependOnClassesThat().resideInAPackage("..promotions.adapter..");

    @ArchTest
    static final ArchRule application_should_only_depend_on_application_domain_shared_java_and_ee = classes()
            .that().resideInAPackage("..promotions.application..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(APPLICATION_ALLOWED_DEPENDENCIES);

    @ArchTest
    static final ArchRule ports_should_be_interfaces = classes()
            .that().resideInAnyPackage("..promotions.domain.port..")
            .should().beInterfaces();

    @ArchTest
    static final ArchRule adapters_should_implement_interfaces = classes()
            .that().resideInAPackage("..promotions.adapter..")
            .and().haveSimpleNameEndingWith("Adapter")
            .should().implement(INTERFACES);

    @ArchTest
    static final ArchRule jpa_entities_only_used_in_persistence_adapter = noClasses()
            .that().resideOutsideOfPackage("..promotions.adapter.out.persistence..")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("JpaEntity");
}
