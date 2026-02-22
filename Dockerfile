FROM openjdk:26-ea-21-oraclelinux8
WORKDIR /opt/app
COPY zvonok-0.0.1-SNAPSHOT.jar /opt/app/zvonok.jar
ENTRYPOINT ["java", "-jar", "zvonok.jar"]
