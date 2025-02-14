FROM maven:3.9.9-eclipse-temurin-23 AS build
COPY src /home/app/src
COPY pom.xml /home/app
RUN mvn -f /home/app/pom.xml clean package

FROM eclipse-temurin:23.0.1_11-jdk
WORKDIR /opt
EXPOSE 8080
COPY --from=build /home/app/target/*.jar /opt/app.jar
CMD ["java", "-jar", "/opt/app.jar"]