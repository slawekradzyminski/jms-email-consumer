FROM eclipse-temurin:25-jdk-jammy@sha256:f122992af75e61d87892f8a37c60f7cfa498b18748c1c9f8563da9a3b1893278 AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw --batch-mode dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode package -DskipTests

FROM eclipse-temurin:25-jre-noble@sha256:fbcf915c585659b30eb766ada4d6d7cfc9ec1040bf521e95bf61b10a25af73db
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/consumer.jar app.jar
USER 10001:10001
EXPOSE 4002
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
