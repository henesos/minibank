# Build stage
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /build

# Copy pom and download dependencies
COPY user-service/pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY user-service/src ./src
RUN mvn package -DskipTests

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create non-root user for security
RUN addgroup -S minibank && adduser -S minibank -G minibank
USER minibank:minibank

# Copy JAR from build stage
COPY --from=build /build/target/*.jar app.jar

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget -q --spider http://localhost:8081/actuator/health || exit 1

# Expose port
EXPOSE 8081

# JVM options for container
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
