FROM maven:3.8.7-openjdk-18-slim AS builder

WORKDIR /app

COPY ./pom.xml .
RUN mvn dependency:resolve

COPY ./src ./src
RUN mvn clean package -DskipTests

FROM quay.io/keycloak/keycloak:26.0.0

COPY --from=builder /app/target/*.jar /opt/keycloak/providers
