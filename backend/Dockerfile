# Use the official Eclipse Temurin Java 21 image for the build stage
FROM eclipse-temurin:21-jdk AS builder

# Set the working directory
WORKDIR /app

# Copy the Gradle wrapper files
COPY gradlew .
COPY gradle gradle

# Copy the build configuration files
COPY build.gradle settings.gradle ./

# Make the wrapper executable and download dependencies
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon || true

# Copy the source code
COPY src src

# Build the application (skipping tests for faster build)
RUN ./gradlew bootJar --no-daemon -x test

# Use a smaller JRE image for the runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the built JAR file from the builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Expose the application port (default is 8080)
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
