FROM eclipse-temurin:22-jdk-alpine as build

WORKDIR /app

# Copy gradle build files
COPY gradlew ./
COPY gradle gradle
COPY build.gradle.kts ./
COPY settings.gradle.kts ./

# Copy source code
COPY src src

# Run the gradle build with shadow jar
RUN chmod +x ./gradlew && ./gradlew shadowJar

# Create the runtime container
FROM eclipse-temurin:22-jre-alpine

WORKDIR /app

# Copy the jar from the build stage
COPY --from=build /app/build/libs/telegram-bot-listener.jar .

# Run the application
CMD ["java", "-jar", "telegram-bot-listener.jar"]