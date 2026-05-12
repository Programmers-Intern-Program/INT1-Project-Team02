package com.flodiback.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import jakarta.persistence.Entity;

@AnalyzeClasses(packages = "com.flodiback", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule api_controllers_should_stay_in_api_packages = classes()
            .that()
            .areAnnotatedWith(RestController.class)
            .should()
            .resideInAPackage("com.flodiback.api..")
            .because("HTTP 엔트리포인트는 API 계층에 둡니다.");

    @ArchTest
    static final ArchRule domain_must_not_depend_on_outer_layers = noClasses()
            .that()
            .resideInAPackage("com.flodiback.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "com.flodiback.global.rsData..",
                    "com.flodiback.global.globalExceptionHandler..",
                    "com.flodiback.api..",
                    "org.springframework.http..",
                    "org.springframework.web..")
            .because("도메인 기능 모듈은 HTTP/API/응답 포맷을 모르는 구현 영역으로 유지합니다.");

    @ArchTest
    static final ArchRule global_core_must_not_depend_on_project_layers = noClasses()
            .that()
            .resideInAnyPackage(
                    "com.flodiback.global.exception..",
                    "com.flodiback.global.rsData..",
                    "com.flodiback.global.globalExceptionHandler..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.flodiback.api..", "com.flodiback.domain..")
            .because("공통 계층은 프로젝트 세부 계층에 의존하면 안 됩니다.");

    @ArchTest
    static final ArchRule entity_classes_should_stay_in_domain_entity_packages = classes()
            .that()
            .areAnnotatedWith(Entity.class)
            .should()
            .resideInAPackage("com.flodiback.domain..entity..")
            .because("@Entity 클래스는 탐색성과 일관성을 위해 entity 패키지에 둡니다.");
}
