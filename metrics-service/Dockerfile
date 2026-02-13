# =========================
# BUILD STAGE
# =========================
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN ./mvnw dependency:go-offline

COPY src src
RUN ./mvnw clean package -DskipTests

# =========================
# RUNTIME STAGE
# =========================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/metrics-service-*.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-Dspring.profiles.active=docker", "-jar", "app.jar"]