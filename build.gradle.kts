plugins {
    java
    id("org.springframework.boot") version "3.5.14"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.1.0"
}

spotless {
    java {
        target(
            "src/main/java/**/*.java",
            "src/test/java/**/*.java"
        )

        palantirJavaFormat()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
        formatAnnotations()

        importOrder("java", "javax", "org", "com", "")
    }

    format("yaml") {
        target("**/*.yml", "**/*.yaml")
        trimTrailingWhitespace()
        endWithNewline()
    }

    format("misc") {
        target("*.gradle", "*.gradle.kts", "*.md", ".gitignore")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

group = "com"
version = "0.0.1-SNAPSHOT"
description = "flodi-back"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.pgvector:pgvector:0.1.6")
    compileOnly("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.2")
    testCompileOnly("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor("org.projectlombok:lombok")
    implementation("org.springframework.boot:spring-boot-starter-validation")

}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register("installGitHooks") {
    doLast {
        val hook = file(".git/hooks/pre-commit")
        hook.writeText(
            """
            #!/bin/sh
            ./gradlew spotlessApply
            git add -u
            """.trimIndent()
        )
        hook.setExecutable(true)
    }
}
