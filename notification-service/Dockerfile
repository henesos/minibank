# Build stage
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /build

# Copy parent pom first
COPY pom.xml .
COPY notification-service/pom.xml notification-service/

# Download dependencies
RUN mvn -f notification-service/pom.xml dependency:go-offline -B

# Copy source and build
COPY notification-service/src notification-service/src
RUN mvn -f notification-service/pom.xml package -DskipTests

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create non-root user for security
RUN addgroup -S minibank && adduser -S minibank -G minibank
USER minibank:minibank

# Copy JAR from build stage
COPY --from=build /build/notification-service/target/*.jar app.jar

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget -q --spider http://localhost:8084/actuator/health || exit 1

# Expose port
EXPOSE 8084

# JVM options for container
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
