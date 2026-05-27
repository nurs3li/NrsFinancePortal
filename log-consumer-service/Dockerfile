# =========================
# BUILD (repo kökü — Maven imajı, wrapper yok)
# =========================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY log-consumer-service/pom.xml log-consumer-service/pom.xml
COPY finance-service/pom.xml finance-service/pom.xml
COPY marketdata/pom.xml marketdata/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml

RUN mkdir -p finance-service/src/main/java \
    marketdata/src/main/java \
    notification-service/src/main/java

RUN mvn dependency:go-offline -pl log-consumer-service -am -q

COPY log-consumer-service/src log-consumer-service/src
RUN mvn package -pl log-consumer-service -am -DskipTests -q

# =========================
# RUNTIME
# =========================
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /app/log-consumer-service/target/log-consumer-service-*.jar app.jar

EXPOSE 8087
ENTRYPOINT ["java", "-jar", "app.jar"]
