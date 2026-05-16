# =========================
# BUILD (repo kökü — parent reactor + yalnızca finance-service kaynağı)
# =========================
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY finance-service/pom.xml finance-service/pom.xml
COPY marketdata/pom.xml marketdata/pom.xml
COPY log-consumer-service/pom.xml log-consumer-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml

# Reactor modülleri için minimal dizin (parent POM doğrulaması)
RUN mkdir -p marketdata/src/main/java \
    log-consumer-service/src/main/java \
    notification-service/src/main/java

RUN chmod +x mvnw && ./mvnw dependency:go-offline -pl finance-service -am -q

COPY finance-service/src finance-service/src
RUN ./mvnw package -pl finance-service -am -DskipTests -q

# =========================
# RUNTIME
# =========================
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /app/finance-service/target/finance-service-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
