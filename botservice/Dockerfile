# syntax=docker/dockerfile:1
FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
# Maven reads all reactor POMs, but only builds this service and its dependencies.
COPY sharedlib/pom.xml ./sharedlib/pom.xml
COPY userservice/pom.xml ./userservice/pom.xml
COPY tripservice/pom.xml ./tripservice/pom.xml
COPY expensesservice/pom.xml ./expensesservice/pom.xml
COPY botservice/pom.xml ./botservice/pom.xml
COPY notificationservice/pom.xml ./notificationservice/pom.xml
COPY weatherservice/pom.xml ./weatherservice/pom.xml
COPY currencyservice/pom.xml ./currencyservice/pom.xml
COPY sharedlib/src ./sharedlib/src
COPY botservice/src ./botservice/src
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn --batch-mode --no-transfer-progress -pl botservice -am -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN mkdir -p /app/bot-state && chown -R 10001:10001 /app
COPY --from=build --chown=10001:10001 /workspace/botservice/target/*.jar /app/app.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
