# Stage 1: Build the Maven packages
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY common/pom.xml common/
COPY domain/pom.xml domain/
COPY auth/pom.xml auth/
COPY core/pom.xml core/
COPY routing/pom.xml routing/
COPY analytics/pom.xml analytics/
COPY realtime/pom.xml realtime/
COPY notification/pom.xml notification/
COPY api/pom.xml api/

# Cache dependencies offline
RUN mvn dependency:go-offline -B

# Copy source code and build package
COPY . .
RUN mvn clean package -DskipTests

# Stage 2: Runtime image
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/api/target/api-2.1.0.jar app.jar
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "app.jar"]
