# GraalVM native image (default published image). The JVM image remains available as Dockerfile.jvm.
# native-image does not cross-compile: build each platform on a runner of that architecture
# (see .github/workflows/publish-image.yml) rather than under QEMU emulation.
FROM ghcr.io/graalvm/native-image-community:25@sha256:0d936f32bb8acb5bc60c41b33e05f064d7a6aaf36b726538296c54949bd4a3c0 AS build
# OpenJDK CVE-2026-91403 mitigation; pinned artifact provenance is in security/java/.
# The builder JDK's security files are what the native image is built against.
COPY security/java/blocked.certs ${JAVA_HOME}/lib/security/blocked.certs
RUN echo "2596bb6dda83c66d96c5a252d7b2ae1f24cc1ae3f1890223b4f3591a8d52bf10  ${JAVA_HOME}/lib/security/blocked.certs" | sha256sum --check -
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw --batch-mode -Pnative dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode -Pnative -DskipTests package \
    && mkdir /workspace/dist \
    && cp target/consumer target/*.so /workspace/dist/

# Distroless glibc base: the executable is linked with --static-nolibc, so it only needs libc.
FROM gcr.io/distroless/base-debian13:nonroot@sha256:9039a88709bcc2bacf5a2faac331d65dd6840f763f225ad9594cc0c67bcd0fba
WORKDIR /app
# The executable plus the JDK shared libraries native-image emits next to it (for example libmanagement_ext.so,
# loaded lazily by OperatingSystemMXBean-backed metrics); they are looked up in the executable's directory.
COPY --from=build --chown=10001:10001 /workspace/dist/ /app/
USER 10001:10001
EXPOSE 4002
ENTRYPOINT ["/app/consumer"]
