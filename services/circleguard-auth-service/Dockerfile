FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build \
  /app/services/circleguard-auth-service/build/libs/*.jar \
  app.jar

EXPOSE 8180

ENTRYPOINT ["java", "-jar", "app.jar"]