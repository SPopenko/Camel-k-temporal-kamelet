FROM maven:3.9.11-eclipse-temurin-17 AS build

ARG APP_MODULE
WORKDIR /workspace

COPY pom.xml .
COPY src src
COPY e2e/apps e2e/apps

RUN mvn -q -DskipTests -Dmaven.repo.local=/tmp/m2 install
RUN mvn -q -f e2e/apps/pom.xml -Dmaven.repo.local=/tmp/m2 -pl ${APP_MODULE} -am package

FROM eclipse-temurin:17-jre

ARG APP_MODULE
WORKDIR /deployments

COPY --from=build /workspace/e2e/apps/${APP_MODULE}/target/app.jar /deployments/app.jar

ENTRYPOINT ["java", "-jar", "/deployments/app.jar"]
