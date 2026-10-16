# syntax=docker/dockerfile:1.7
# ApexPath LIMS backend: multi-stage build -> minimal non-root JRE image.
# Build context: ./backend

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
# Tests run in CI (mvn verify); the image build only packages. The BuildKit cache mount keeps
# ~/.m2 between builds, so dependencies are not re-downloaded on every source change.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -DskipTests -pl lims-server -am package \
 && cp lims-server/target/lims-server-*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system lims && useradd --system --gid lims --home-dir /app --shell /usr/sbin/nologin lims \
 && apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build --chown=lims:lims /workspace/app.jar app.jar
USER lims

ENV SERVER_PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8080

# Liveness probe exposed by Spring Boot Actuator (management.endpoint.health.probes.enabled)
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
