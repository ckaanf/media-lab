FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN chmod +x ./gradlew
RUN ./gradlew clean bootJar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache ffmpeg bash

COPY --from=build /app/build/libs/*.jar app.jar

RUN mkdir -p /tmp/hls

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]