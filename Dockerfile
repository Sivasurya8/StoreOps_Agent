# Stage 1: Build application
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Production Runtime
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Create non-root system user
RUN groupadd -r kiranapilot && useradd -r -g kiranapilot kiranapilot
USER kiranapilot

COPY --from=build /app/target/kirana-pilot-agent-1.0.0.jar app.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
