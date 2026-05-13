# Stage 1: Build
FROM gradle:8.14-jdk21 AS build

WORKDIR /app

# Copiar solo archivos necesarios primero (cache Docker)
COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle gradle

# Copiar únicamente el servicio necesario
COPY services/circleguard-auth-service services/circleguard-auth-service

# Dar permisos
RUN chmod +x gradlew

# Build
RUN ./gradlew :services:circleguard-auth-service:bootJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build \
  /app/services/circleguard-auth-service/build/libs/*.jar \
  app.jar

EXPOSE 8180

ENTRYPOINT ["java", "-jar", "app.jar"]