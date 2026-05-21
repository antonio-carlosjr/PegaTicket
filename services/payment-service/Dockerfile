# syntax=docker/dockerfile:1.7
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
COPY shared/common-lib/pom.xml shared/common-lib/
COPY services/api-gateway/pom.xml     services/api-gateway/
COPY services/user-service/pom.xml    services/user-service/
COPY services/event-service/pom.xml   services/event-service/
COPY services/ticket-service/pom.xml  services/ticket-service/
COPY services/payment-service/pom.xml services/payment-service/

COPY shared/common-lib/src        shared/common-lib/src
COPY services/payment-service/src services/payment-service/src

RUN --mount=type=cache,id=ticketeira-m2,target=/root/.m2 \
    mvn -B -ntp -pl services/payment-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/services/payment-service/target/payment-service.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java","-jar","/app/app.jar"]
