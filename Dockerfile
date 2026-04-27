FROM maven:3.9.9-eclipse-temurin-21 as build

WORKDIR /app

COPY pom.xml
COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /opt/app

COPY target/zvonok-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
