FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build \
  /app/services/circleguard-file-service/build/libs/*.jar \
  app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]