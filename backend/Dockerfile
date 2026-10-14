# =====================================================================================
# Backend image: a MULTI-STAGE build.
#   Stage 1 ("build") has the full JDK + Maven and compiles the fat jar.
#   Stage 2 only has a JRE and the jar. Maven, the source code and the downloaded
#   dependencies' caches stay behind in stage 1, so the final image is small and has
#   less attack surface.
# =====================================================================================

# ---------- Stage 1: build the jar ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# Copy pom.xml first and download dependencies. Docker caches this layer, so dependencies are
# only downloaded again when pom.xml changes, not on every code change.
# (Inside this image we use its own Maven; ./mvnw would download Maven a second time.)
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
# Tests run in CI (.github/workflows/ci.yml); skipping them here keeps image builds fast.
RUN mvn -q -B package -DskipTests

# ---------- Stage 2: run it on a small JRE-only image ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Security best practice: never run the application as root inside the container.
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/*.jar app.jar
USER app
# Documentation only: the prod profile listens on 8080 (or $PORT on Render/Railway).
EXPOSE 8080
# The office time zone used for "now" in booking rules can be set with the TZ env variable.
ENTRYPOINT ["java", "-jar", "app.jar"]
