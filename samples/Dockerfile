FROM maven:3.9.11-eclipse-temurin-17 AS build

ARG APP_MODULE
WORKDIR /workspace

COPY pom.xml .
COPY src src
COPY samples samples

RUN mvn -q -DskipTests -Dmaven.repo.local=/tmp/m2 install
RUN mvn -q -f samples/pom.xml -Dmaven.repo.local=/tmp/m2 -pl ${APP_MODULE} -am package

FROM eclipse-temurin:17-jre

ARG APP_MODULE
WORKDIR /deployments

COPY --from=build /workspace/samples/${APP_MODULE}/target/app.jar /deployments/app.jar

ENTRYPOINT ["java", "-jar", "/deployments/app.jar"]
