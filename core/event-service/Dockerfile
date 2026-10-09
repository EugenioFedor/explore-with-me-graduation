FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY . .

RUN mvn -pl core/event-service -am package -DskipTests


FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/core/event-service/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]