package com.loja.admindashboard;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.annotation.security.RolesAllowed;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.loja",
        importOptions = {ImportOption.DoNotIncludeTests.class})
public class AdminHexagonalArchitectureTest {

    @ArchTest
    static final ArchRule admin_should_not_depend_on_jpa_entities_of_other_modules = noClasses()
            .that().resideInAPackage("..admindashboard..")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("JpaEntity");

    @ArchTest
    static final ArchRule admin_should_not_depend_on_repositories_of_other_modules = noClasses()
            .that().resideInAPackage("..admindashboard..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out.persistence..");

    @ArchTest
    static final ArchRule admin_beans_should_be_in_web_adapter = classes()
            .that().resideInAPackage("..admindashboard..")
            .and().haveSimpleNameEndingWith("Bean")
            .should().resideInAPackage("..admindashboard.adapter.in.web..");

    @ArchTest
    static final ArchRule admin_beans_must_be_roles_allowed = classes()
            .that().resideInAPackage("..admindashboard.adapter.in.web..")
            .and().haveSimpleNameEndingWith("Bean")
            .should().beAnnotatedWith(RolesAllowed.class);
}
