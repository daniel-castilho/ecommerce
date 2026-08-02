package com.loja.productcatalog;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.INTERFACES;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architectural guard rails: enforces the hexagonal layout of the product-catalog module
 * (spec §0 and §13). Fails the build if the boundaries (domain / application / adapter)
 * are crossed. Mirrors {@code UserHexagonalArchitectureTest} (user-account) plus the
 * product-catalog-specific JPA-entity rule.
 */
@AnalyzeClasses(packages = "com.loja.productcatalog",
        importOptions = {ImportOption.DoNotIncludeTests.class})
public class ProductHexagonalArchitectureTest {

    private static final String[] EE_API = {"jakarta..", "javax.."};

    private static final String[] DOMAIN_ALLOWED_DEPENDENCIES = {
            "..productcatalog.domain..",
            "..productcatalog.application.dto..",
            "com.loja.shared..",
            "java..",
    };

    private static final String[] APPLICATION_ALLOWED_DEPENDENCIES = {
            "..productcatalog.application..",
            "..productcatalog.domain..",
            "com.loja.shared..",
            "org.owasp..",
            "jakarta..",
            "java..",
    };

    @ArchTest
    static final ArchRule domain_should_not_depend_on_any_ee_api = noClasses()
            .that().resideInAPackage("..productcatalog.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(EE_API);

    @ArchTest
    static final ArchRule domain_should_not_depend_on_adapters = noClasses()
            .that().resideInAPackage("..productcatalog.domain..")
            .should().dependOnClassesThat().resideInAPackage("..productcatalog.adapter..");

    @ArchTest
    static final ArchRule domain_should_only_depend_on_domain_shared_and_java = classes()
            .that().resideInAPackage("..productcatalog.domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(DOMAIN_ALLOWED_DEPENDENCIES);

    @ArchTest
    static final ArchRule application_should_not_depend_on_adapters = noClasses()
            .that().resideInAPackage("..productcatalog.application..")
            .should().dependOnClassesThat().resideInAPackage("..productcatalog.adapter..");

    @ArchTest
    static final ArchRule application_should_only_depend_on_application_domain_shared_java_and_ee = classes()
            .that().resideInAPackage("..productcatalog.application..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(APPLICATION_ALLOWED_DEPENDENCIES);

    @ArchTest
    static final ArchRule ports_should_be_interfaces = classes()
            .that().resideInAnyPackage("..productcatalog.domain.port..")
            .should().beInterfaces();

    @ArchTest
    static final ArchRule adapters_should_implement_interfaces = classes()
            .that().resideInAPackage("..productcatalog.adapter..")
            .and().haveSimpleNameEndingWith("Adapter")
            .should().implement(INTERFACES);

    @ArchTest
    static final ArchRule jpa_entities_only_used_in_persistence_adapter = noClasses()
            .that().resideOutsideOfPackage("..productcatalog.adapter.out.persistence..")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("JpaEntity");
}
