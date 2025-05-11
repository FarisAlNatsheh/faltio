FROM maven:3.9.9-eclipse-temurin-21 AS builder
COPY src /home/app/src
COPY pom.xml /home/app

RUN mvn -f /home/app/pom.xml package -DskipTests
WORKDIR /home/app/target


FROM eclipse-temurin:21-jre-alpine

USER root
COPY --from=builder /home/app/target/ ./
RUN true

EXPOSE 8080
USER 1001

ENTRYPOINT ["java", "-jar", "faltio-operator-0.1.0-SNAPSHOT.jar"]