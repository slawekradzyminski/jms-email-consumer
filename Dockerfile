FROM eclipse-temurin:25-jdk-jammy AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw --batch-mode dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode package -DskipTests

FROM eclipse-temurin:25-jre-noble
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/consumer.jar app.jar
USER 10001:10001
EXPOSE 4002
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
