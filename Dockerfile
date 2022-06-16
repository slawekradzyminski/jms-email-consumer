FROM eclipse-temurin:17-jre-focal
COPY target/consumer-1.0.0.jar consumer-1.0.0.jar
ENTRYPOINT ["java","-jar","/consumer-1.0.0.jar"]