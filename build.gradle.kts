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
    // 2. 리포트 생성 전 테스트가 반드시 실행되도록 설정
    dependsOn(tasks.test)

    reports {
        // html 리포트 활성화 (브라우저로 볼 용도)
        html.required.set(true)
        // xml 리포트 활성화 (SonarQube 등 연동용)
        xml.required.set(true)

        // 리포트 저장 위치를 바꾸고 싶다면 (기본값은 build/reports/jacoco)
        // html.outputLocation.set(layout.buildDirectory.dir("jacocoHtml"))
    }

    // 3. 리포트에서 제외할 클래스 설정 (QueryDSL, DTO 등)
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/dto/**",
                    "**/config/**",
                    "**/*Application*",
                    "**/Q*" // QueryDSL용
                )
            }
        })
    )
}

tasks.test {
    // 4. 테스트 완료 후 자동으로 Jacoco 리포트 생성
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
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.pgvector:pgvector:0.1.6")
    compileOnly("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Java 25(class file 69) 호환을 위해 ArchUnit 버전을 상향함
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.2")
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
