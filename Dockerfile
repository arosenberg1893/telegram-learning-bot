# ---- Этап сборки ----
FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# ---- Этап выполнения ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Устанавливаем PostgreSQL client (pg_dump, pg_restore)
RUN apk add --no-cache postgresql16-client

COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]