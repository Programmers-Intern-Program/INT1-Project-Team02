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
        POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // pgvector 익스텐션은 DB 전체 범위라 커넥션 생성 시 한 번만 실행해도 충분
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "CREATE EXTENSION IF NOT EXISTS vector");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }
}
