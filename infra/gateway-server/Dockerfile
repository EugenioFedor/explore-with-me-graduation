FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY . .

RUN mvn -pl infra/gateway-server -am package -DskipTests


FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/infra/gateway-server/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]