FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build \
  /app/services/circleguard-form-service/build/libs/*.jar \
  app.jar

EXPOSE 8086

ENTRYPOINT ["java", "-jar", "app.jar"]