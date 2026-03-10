# -------------------------------------------------
# Stage 1: Extract Spring Boot Layers
# -------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS builder

WORKDIR /app

ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar

# Extract Spring Boot layered jar
RUN java -Djarmode=layertools -jar app.jar extract


# -------------------------------------------------
# Stage 2: Runtime Image
# -------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Install curl for health check
RUN apk add --no-cache curl

# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

# Copy layers with correct ownership (avoids large chown layer)
COPY --from=builder --chown=spring:spring /app/dependencies/ ./
COPY --from=builder --chown=spring:spring /app/snapshot-dependencies/ ./
COPY --from=builder --chown=spring:spring /app/spring-boot-loader/ ./
COPY --from=builder --chown=spring:spring /app/application/ ./

USER spring

EXPOSE 8084

# JVM tuning for containers
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-XX:+UseG1GC","-Djava.security.egd=file:/dev/./urandom","org.springframework.boot.loader.launch.JarLauncher"]

# Health check using Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
CMD curl -f http://localhost:8080/actuator/health || exit 1