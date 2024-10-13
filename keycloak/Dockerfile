FROM maven:3.8.7-openjdk-18-slim as builder

WORKDIR /opt/productdock/

COPY ./pom.xml .
RUN mvn dependency:resolve

COPY . .
RUN mvn clean package -DskipTests

FROM quay.io/keycloak/keycloak:26.0.0

COPY --from=builder /opt/productdock/target/*.jar /opt/keycloak/providers
