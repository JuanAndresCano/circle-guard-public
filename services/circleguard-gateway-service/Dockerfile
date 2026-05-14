FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build \
  /app/services/circleguard-gateway-service/build/libs/*.jar \
  app.jar

EXPOSE 8087

ENTRYPOINT ["java", "-jar", "app.jar"]