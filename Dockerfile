# syntax=docker/dockerfile:1
FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY sharedlib ./sharedlib
COPY userservice ./userservice
COPY tripservice ./tripservice
COPY expensesservice ./expensesservice
COPY botservice ./botservice
COPY notificationservice ./notificationservice
COPY weatherservice ./weatherservice
COPY currencyservice ./currencyservice
# Reused by all seven runtime images. No application secrets enter this stage.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn --batch-mode --no-transfer-progress -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN mkdir -p /app/bot-state && chown -R 10001:10001 /app
ARG SERVICE
COPY --from=build --chown=10001:10001 /workspace/${SERVICE}/target/*.jar /app/app.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
