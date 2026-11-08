FROM eclipse-temurin:25-jdk-jammy@sha256:e4f029273ed9e4444697df30bb6098dbe51477291598aebc54dd0b48d95d6ae7 AS build
# OpenJDK CVE-2026-91403 mitigation; pinned artifact provenance is in security/java/.
COPY security/java/blocked.certs ${JAVA_HOME}/lib/security/blocked.certs
RUN echo "2596bb6dda83c66d96c5a252d7b2ae1f24cc1ae3f1890223b4f3591a8d52bf10  ${JAVA_HOME}/lib/security/blocked.certs" | sha256sum --check -
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw --batch-mode dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode package -DskipTests

FROM eclipse-temurin:25-jre-noble@sha256:a98128a57de944a72bd31fbc79b898edd5a5864d3652071d5bbd2160ab7d7dce
# OpenJDK CVE-2026-91403 mitigation; pinned artifact provenance is in security/java/.
COPY security/java/blocked.certs ${JAVA_HOME}/lib/security/blocked.certs
RUN echo "2596bb6dda83c66d96c5a252d7b2ae1f24cc1ae3f1890223b4f3591a8d52bf10  ${JAVA_HOME}/lib/security/blocked.certs" | sha256sum --check -
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/consumer.jar app.jar
USER 10001:10001
EXPOSE 4002
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
