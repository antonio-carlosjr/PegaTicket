# syntax=docker/dockerfile:1.7
# ---- Stage 1: build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copia poms primeiro para aproveitar cache de dependencias
COPY pom.xml .
COPY shared/common-lib/pom.xml shared/common-lib/
COPY services/api-gateway/pom.xml     services/api-gateway/
COPY services/user-service/pom.xml    services/user-service/
COPY services/event-service/pom.xml   services/event-service/
COPY services/ticket-service/pom.xml  services/ticket-service/
COPY services/payment-service/pom.xml services/payment-service/

# Fontes necessarias (common-lib + este servico)
COPY shared/common-lib/src        shared/common-lib/src
COPY services/api-gateway/src     services/api-gateway/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -pl services/api-gateway -am package -DskipTests

# ---- Stage 2: runtime ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/services/api-gateway/target/api-gateway.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
