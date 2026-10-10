FROM eclipse-temurin:25-jdk-jammy@sha256:318f90a80337fb6f26b025dbec0a95fa6f48abf60ce129babf555618356bdbf8 AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw --batch-mode dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode package -DskipTests

FROM eclipse-temurin:25-jre-noble@sha256:d9a39a23634650173f1e2bbc176227af9728587ecf0f4b62d53e9355cd7a19ab
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/consumer.jar app.jar
USER 10001:10001
EXPOSE 4002
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
