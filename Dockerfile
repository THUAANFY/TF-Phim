FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY tfphim ./tfphim
WORKDIR /app/tfphim

RUN chmod +x mvnw
RUN ./mvnw -q -DskipTests dependency:go-offline
RUN ./mvnw -q -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app

ENV PORT=8080

COPY --from=build /app/tfphim/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
