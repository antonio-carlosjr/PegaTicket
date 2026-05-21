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
COPY services/ticket-service/src  services/ticket-service/src

RUN mvn -B -ntp -pl services/ticket-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/services/ticket-service/target/ticket-service.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java","-jar","/app/app.jar"]
