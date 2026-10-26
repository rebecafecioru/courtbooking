# Backend image. Build:  docker build -t courtbooking-backend .
# Tests aren't run here; CI runs them on every push.

# 1. Build the jar with the full JDK
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
# Download dependencies in their own layer, so changing the code doesn't
# re-download them on every build
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar

# 2. Run it on the smaller JRE, as a non-root user
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=build /app/build/libs/*.jar app.jar
USER app
# Real database settings come from environment variables; see
# application-postgres.properties and docker-compose.yml
ENV SPRING_PROFILES_ACTIVE=postgres
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
