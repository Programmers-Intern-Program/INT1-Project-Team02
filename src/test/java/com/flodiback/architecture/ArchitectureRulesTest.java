package com.flodiback.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import jakarta.persistence.Entity;

@AnalyzeClasses(packages = "com.flodiback", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule global_core_must_not_depend_on_domain = noClasses()
            .that()
            .resideInAnyPackage(
                    "com.flodiback.global.exception..",
                    "com.flodiback.global.rsData..",
                    "com.flodiback.global.globalExceptionHandler..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.flodiback.domain..")
            .because("공통 계층은 도메인 상세 구현에 의존하면 안 됩니다.");

    @ArchTest
    static final ArchRule domain_must_not_depend_on_global_core = noClasses()
            .that()
            .resideInAPackage("com.flodiback.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "com.flodiback.global.exception..",
                    "com.flodiback.global.rsData..",
                    "com.flodiback.global.globalExceptionHandler..")
            .because("도메인은 공통 예외/응답 구현 대신 도메인 규칙 중심으로 유지해야 합니다.");

    @ArchTest
    static final ArchRule entity_classes_should_stay_in_entity_packages = noClasses()
            .that()
            .areAnnotatedWith(Entity.class)
            .should()
            .resideOutsideOfPackage("..entity..")
            .because("@Entity 클래스는 탐색성과 일관성을 위해 entity 패키지에 둡니다.");
}
