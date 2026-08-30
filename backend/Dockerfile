# Multi-stage build: Maven+JDK build stage produces the Spring Boot fat jar,
# which is copied into a slim JRE runtime stage (no build toolchain ships in
# the final image). Story 4.1 - see _bmad-output/implementation-artifacts/
# spec-4-1-backend-and-frontend-dockerfiles.md.

# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy the POM first so dependency resolution is cached across rebuilds that
# only change source files.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src

# Tests are intentionally skipped here: Testcontainers-based repository tests
# need a Docker daemon reachable from inside the build, which is out of scope
# for this story. CI's `mvn test` already covers correctness.
RUN mvn -B -DskipTests package

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Run as a non-root user rather than the image's default root.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
