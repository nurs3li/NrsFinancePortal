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
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/marketdata-*.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java","-jar","/app/app.jar"]