package com.flodiback.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.springframework.data.jpa.repository.JpaRepository;
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
                    "com.flodiback.application..",
                    "com.flodiback.infrastructure..",
                    "org.springframework.web..")
            .because("도메인은 HTTP/API/응답 포맷과 기술 구현을 모르는 비즈니스 중심 계층이어야 합니다.");

    @ArchTest
    static final ArchRule application_must_not_depend_on_api_or_infrastructure = noClasses()
            .that()
            .resideInAPackage("com.flodiback.application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "com.flodiback.api..",
                    "com.flodiback.infrastructure..",
                    "com.flodiback.global.rsData..",
                    "org.springframework.web..")
            .because("application 계층은 유스케이스를 조율하되 HTTP 계약과 기술 구현에 의존하지 않습니다.");

    @ArchTest
    static final ArchRule global_core_must_not_depend_on_project_layers = noClasses()
            .that()
            .resideInAnyPackage(
                    "com.flodiback.global.exception..",
                    "com.flodiback.global.rsData..",
                    "com.flodiback.global.globalExceptionHandler..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "com.flodiback.api..",
                    "com.flodiback.application..",
                    "com.flodiback.domain..",
                    "com.flodiback.infrastructure..")
            .because("공통 계층은 프로젝트 세부 계층에 의존하면 안 됩니다.");

    @ArchTest
    static final ArchRule entity_classes_should_stay_in_domain_entity_packages = classes()
            .that()
            .areAnnotatedWith(Entity.class)
            .should()
            .resideInAPackage("com.flodiback.domain..entity..")
            .because("@Entity 클래스는 탐색성과 일관성을 위해 entity 패키지에 둡니다.");

    @ArchTest
    static final ArchRule jpa_repositories_should_stay_in_infrastructure = classes()
            .that()
            .areAssignableTo(JpaRepository.class)
            .should()
            .resideInAPackage("com.flodiback.infrastructure.persistence..")
            .because("Spring Data JPA 구현은 infrastructure persistence 계층에 둡니다.");
}
