# ---- Build stage: compile and package the jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# Copy the pom first so dependency downloads are cached until pom.xml changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Tests run in CI, not inside the image build.
RUN mvn -B -q package -DskipTests

# ---- Runtime stage: small JRE image, non-root user ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S cloudpilot && adduser -S cloudpilot -G cloudpilot
WORKDIR /app
COPY --from=build /src/target/cloudpilot-backend-*.jar app.jar
USER cloudpilot
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=5 \
  CMD wget -qO- http://localhost:${MANAGEMENT_SERVER_PORT:-8080}/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
