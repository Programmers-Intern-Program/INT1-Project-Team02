plugins {
    java
    id("org.springframework.boot") version "3.5.14"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.1.0"
    jacoco
}

jacoco {
    // Jacoco 버전 (2026년 기준 최신 안정화 버전 권장)
    toolVersion = "0.8.14"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        html.required.set(true)
        xml.required.set(true)
    }

    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/dto/**",
                    "**/config/**",
                    "**/*Application*",
                    "**/Q*"
                )
            }
        })
    )
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
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
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("com.openai:openai-java:2.20.1")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.pgvector:pgvector:0.1.6")
    compileOnly("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("org.postgresql:postgresql")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // testContainers
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")

    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.2")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testCompileOnly("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor("org.projectlombok:lombok")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Discord bot (JDA + DAVE)
    // JDA: Discord Gateway/REST 클라이언트 기본 SDK
    implementation("net.dv8tion:JDA:6.4.1")
    // jdave-api: DAVE(E2EE) Java 인터롭 레이어
    implementation("club.minnced:jdave-api:0.1.7")
    // OS별 네이티브 DAVE 구현체
    runtimeOnly("club.minnced:jdave-native-darwin:0.1.7")
    runtimeOnly("club.minnced:jdave-native-linux-x86-64:0.1.7")
    runtimeOnly("club.minnced:jdave-native-linux-aarch64:0.1.7")

}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Spring 서버와 분리된 Discord 봇 실행 태스크
tasks.register<JavaExec>("runDiscordBot") {
    group = "application"
    description = "Runs the Discord bot with JDA + JDAVE"
    classpath = sourceSets.main.get().runtimeClasspath
    // 분리 실행 엔트리포인트
    mainClass.set("com.flodiback.bot.DiscordBotMain")
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    )
    // DAVE 네이티브 라이브러리 접근 허용 (JDK 25)
    jvmArgs("--enable-native-access=ALL-UNNAMED")
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
