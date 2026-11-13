# Build Stage
FROM gradle:8.10-jdk21-alpine AS builder

WORKDIR /app

# Copy gradle files first for caching
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle

# Download dependencies
RUN gradle dependencies --no-daemon

# Copy source code
COPY src ./src

# Build application (skip tests for faster build, run separately in CI)
RUN gradle clean bootJar --no-daemon -x test

# Runtime Stage - Distroless for security
FROM gcr.io/distroless/java21-debian12:nonroot

WORKDIR /app

# Copy built jar from builder
COPY --from=builder /app/build/libs/*.jar app.jar

# Non-root user (distroless uses nonroot:nonroot by default)
USER nonroot:nonroot

# Expose port
EXPOSE 8080

# Health check
# Nota: distroless no incluye shell ni curl, por eso el healthcheck se hace
# desde docker compose / Kubernetes pegándole a /actuator/health.

# Run application
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+UseContainerSupport", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]