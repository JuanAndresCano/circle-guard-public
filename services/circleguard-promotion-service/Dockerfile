FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build \
  /app/services/circleguard-promotion-service/build/libs/*.jar \
  app.jar

EXPOSE 8088

ENTRYPOINT ["java", "-jar", "app.jar"]