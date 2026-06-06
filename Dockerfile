# ── 1단계: 빌드 ──────────────────────────────────────────
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY module-api/build.gradle ./module-api/
COPY module-core/build.gradle ./module-core/
COPY module-infra/build.gradle ./module-infra/
COPY module-worker/build.gradle ./module-worker/
COPY module-support/build.gradle ./module-support/

# 의존성 캐시 레이어 (소스 변경 시에도 재다운로드 방지)
RUN ./gradlew dependencies --no-daemon -q || true

COPY module-api/src ./module-api/src
COPY module-core/src ./module-core/src
COPY module-infra/src ./module-infra/src
COPY module-worker/src ./module-worker/src
COPY module-support/src ./module-support/src

RUN ./gradlew :module-api:bootJar --no-daemon -x test

# ── 2단계: 실행 ──────────────────────────────────────────
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=builder /app/module-api/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
