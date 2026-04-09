# ═══════════════════════════════════════════════════════════════
# backend/Dockerfile
# Spring Boot 멀티스테이지 빌드
#   - Stage 1 (builder): JDK + Gradle로 bootJar 생성
#   - Stage 2 (runtime): JRE만 포함한 경량 이미지로 실행
# ═══════════════════════════════════════════════════════════════

# ── Stage 1: Build ───────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /build

# Gradle Wrapper 먼저 복사 (소스 변경과 분리하여 레이어 캐시 최대화)
COPY gradlew gradlew
COPY gradle/ gradle/
RUN chmod +x gradlew

# 의존성 파일만 먼저 복사 → 소스 변경 시 의존성 재다운로드 방지
COPY build.gradle settings.gradle ./

# BuildKit 캐시 마운트: ~/.gradle 를 빌드 간 공유 → 반복 배포 시 Gradle 의존성 재다운로드 방지
# (Docker BuildKit 활성화 필요, Docker 23+ 기본값)
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies --configuration compileClasspath 2>/dev/null || true

# 소스 복사 및 JAR 빌드
COPY src/ src/
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon clean bootJar -x test

# ── Stage 2: Runtime ─────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

# 헬스체크(curl)용 패키지 설치 후 apt 캐시 정리
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 빌드 결과물 복사
COPY --from=builder /build/build/libs/*.jar app.jar

EXPOSE 8050

# application-dev.properties는 compose에서 볼륨 마운트로 주입
# SPRING_CONFIG_ADDITIONAL_LOCATION은 docker-compose.yml에서 env로 설정
ENTRYPOINT ["java", \
  "-jar", \
  "-Dfile.encoding=UTF-8", \
  "app.jar"]