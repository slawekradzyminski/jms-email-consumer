FROM eclipse-temurin:25-jdk-jammy as build
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:25-jre-noble
WORKDIR /app
COPY --from=build /app/target/consumer-1.0.0.jar consumer-1.0.0.jar
EXPOSE 4002
ENTRYPOINT ["java","-jar","/consumer-1.0.0.jar"]
