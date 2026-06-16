# 벼리 API (Spring Boot 4 / Java 17) — 멀티스테이지 빌드
# 1) gradle 빌드 → bootJar  2) JRE 런타임
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
# 의존성 캐시 레이어: gradle 래퍼/설정 먼저 복사
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies >/dev/null 2>&1 || true
# 소스 복사 후 빌드(테스트 제외)
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Seoul
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
