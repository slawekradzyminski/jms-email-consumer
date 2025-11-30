FROM eclipse-temurin:25-jre-noble
COPY target/consumer-1.0.0.jar consumer-1.0.0.jar
ENTRYPOINT ["java","-jar","/consumer-1.0.0.jar"]
