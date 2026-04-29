package com.flodiback.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * pgvector/pgvector 이미지로 PostgreSQL 컨테이너를 공유하는 통합 테스트 베이스 클래스.
 * 컨테이너는 JVM 당 한 번만 기동되고 모든 하위 테스트 클래스가 재사용한다.
 */
public abstract class AbstractPostgresIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        // withInitScript: 컨테이너 시작 시 한 번만 실행되어 connection-init-sql보다 안정적
        POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16").withInitScript("init-pgvector.sql");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        // Flyway는 테스트에서 비활성화 (Hibernate create-drop이 스키마를 직접 관리)
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("openai.api-key", () -> "test-key");
    }
}
