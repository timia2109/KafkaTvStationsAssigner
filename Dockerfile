FROM gradle:jdk8 AS build
WORKDIR /app
COPY . .
RUN gradle shadowJar --no-daemon

FROM openjdk:8-jre-slim

RUN mkdir /app
COPY --from=build /app/build/libs/*.jar /app
COPY --from=build /app/configuration /app/configuration
ENTRYPOINT [ "java", "-jar", "/app/KafkaTvStationAssigner-0.0.1.jar", "/app/configuration/dev.properties"]