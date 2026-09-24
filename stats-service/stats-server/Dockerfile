FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY . .

RUN mvn -pl stats-service/stats-server -am package -DskipTests


FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/stats-service/stats-server/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]