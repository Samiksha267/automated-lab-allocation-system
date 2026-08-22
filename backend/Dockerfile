# --- Build stage -----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache dependencies separately from source so code-only changes don't
# re-download the internet on every rebuild.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B package -DskipTests

# --- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Do not run the application as root.
RUN addgroup -S app && adduser -S app -G app
USER app

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
