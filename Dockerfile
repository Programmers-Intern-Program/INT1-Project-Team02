# syntax=docker/dockerfile:1.7

# =========================
# 1. JDK 25 Toolchain Stage
# =========================
# Gradle은 JDK 21에서 실행하지만, 프로젝트 컴파일에는 JDK 25 toolchain이 필요합니다.
FROM eclipse-temurin:25-jdk AS jdk25

# =========================
# 2. Build Stage
# =========================
# Gradle Kotlin DSL이 JDK 25 런타임에서 25.0.2 버전 파싱 오류를 낸 이력이 있어 JDK 21로 Gradle을 실행합니다.
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# JDK 25를 builder 안으로 복사해 Gradle Java toolchain으로 사용합니다.
COPY --from=jdk25 /opt/java/openjdk /opt/jdk-25

ENV JAVA_HOME_25_X64=/opt/jdk-25
ENV ORG_GRADLE_OPTS="-Dorg.gradle.java.installations.fromEnv=JAVA_HOME_25_X64"

# 의존성 캐시 레이어입니다. 소스만 바뀌면 이 레이어를 재사용합니다.
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

RUN chmod +x gradlew

# Gradle 의존성을 먼저 받아 Docker layer/cache 효율을 높입니다.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --console=plain dependencies > /dev/null

COPY src src

# 테스트는 CI에서 별도 수행하고, 이미지 빌드에서는 배포 산출물인 bootJar만 만듭니다.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --console=plain bootJar

# =========================
# 3. Layer Extract Stage
# =========================
# Spring Boot layered jar를 풀어 런타임 이미지의 Docker cache 효율을 높입니다.
FROM eclipse-temurin:25-jre AS extractor

WORKDIR /app

COPY --from=builder /app/build/libs /app/libs

RUN find /app/libs -name "*.jar" ! -name "*plain*" -exec cp {} /app/app.jar \; && \
    java -Djarmode=tools -jar app.jar extract --layers --launcher

# =========================
# 4. Runtime Stage
# =========================
# 최종 런타임은 JDK 전체가 아닌 JRE만 사용합니다.
FROM eclipse-temurin:25-jre AS runtime

ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"

WORKDIR /app

# 애플리케이션을 root가 아닌 사용자로 실행합니다.
RUN groupadd --system app && \
    useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app

# Spring Boot layers를 변경 빈도가 낮은 순서대로 복사합니다.
COPY --from=extractor --chown=app:app /app/app/dependencies/ ./
COPY --from=extractor --chown=app:app /app/app/snapshot-dependencies/ ./
COPY --from=extractor --chown=app:app /app/app/spring-boot-loader/ ./
COPY --from=extractor --chown=app:app /app/app/application/ ./

EXPOSE 8080

USER app

ENTRYPOINT ["java", \
  "--enable-native-access=ALL-UNNAMED", \
  "-XX:+UseSerialGC", \
  "-XX:MaxRAMPercentage=55", \
  "-XX:InitialRAMPercentage=25", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-XX:+AlwaysPreTouch", \
  "org.springframework.boot.loader.launch.JarLauncher"]
