# =========================
# BUILD (repo kökü — Maven imajı, wrapper yok)
# =========================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY notification-service/pom.xml notification-service/pom.xml
COPY finance-service/pom.xml finance-service/pom.xml
COPY marketdata/pom.xml marketdata/pom.xml
COPY log-consumer-service/pom.xml log-consumer-service/pom.xml

RUN mkdir -p finance-service/src/main/java \
    marketdata/src/main/java \
    log-consumer-service/src/main/java

RUN mvn dependency:go-offline -pl notification-service -am -q

COPY notification-service/src notification-service/src
RUN mvn package -pl notification-service -am -DskipTests -q

# =========================
# RUNTIME
# =========================
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /app/notification-service/target/notification-service-*.jar app.jar

EXPOSE 8089
ENTRYPOINT ["java", "-jar", "app.jar"]
