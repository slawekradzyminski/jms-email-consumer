## Requirements

- Java 25 (Temurin or compatible)
- Docker (optional, for image builds or local Compose usage; the native image is built inside Docker, so a
  local GraalVM installation is not required)

## Email content

The consumer delivers each email with the subject and body that `test-secure-backend` rendered. It does not
render templates itself: the backend owns every customer email text, so the backend's local outbox (used when no
broker runs) and the delivered mail always match. The `template` field is metadata for logging; unknown template
names are bound as `GENERIC`.

## Runtime contract

The consumer uses one configuration path across localhost runs, Docker/localstack, and server deployment. Transport endpoints are controlled by environment variables, with Docker-friendly defaults baked into `application.yml`.

Supported environment variables:

- `SPRING_ACTIVEMQ_BROKER_URL`
- `SPRING_ACTIVEMQ_USER`
- `SPRING_ACTIVEMQ_PASSWORD`
- `SPRING_MAIL_HOST`
- `SPRING_MAIL_PORT`
- `ACTIVEMQ_DESTINATION`
- `EMAIL_FROM`

Default values:

- `SPRING_ACTIVEMQ_BROKER_URL=tcp://activemq:61616`
- `SPRING_ACTIVEMQ_USER=admin`
- `SPRING_ACTIVEMQ_PASSWORD=admin`
- `SPRING_MAIL_HOST=mailhog`
- `SPRING_MAIL_PORT=1025`
- `ACTIVEMQ_DESTINATION=email`
- `EMAIL_FROM=awesome@testing.com`

## Direct localhost run

Use this when ActiveMQ and Mailhog are exposed on your machine.

```bash
./mvnw clean package
SPRING_ACTIVEMQ_BROKER_URL=tcp://localhost:61616 \
SPRING_MAIL_HOST=localhost \
SPRING_MAIL_PORT=1025 \
java -jar target/consumer.jar
```

If you use different credentials or destination names, override the remaining variables as needed.

## Docker or localstack-style run

No extra env vars are required when the app runs in a Compose network where `activemq` and `mailhog` resolve by service name.

```bash
docker build --tag=consumer:latest .            # GraalVM native executable (published image)
docker build --file Dockerfile.jvm --tag=consumer:jvm .   # JVM fallback
docker run -p4002:4002 consumer:latest
```

Both Dockerfiles are multi-stage and compile the sources themselves; no local `./mvnw package` is needed.

If you run the container outside the Compose network, pass explicit transport variables:

```bash
docker run \
  -p4002:4002 \
  -e SPRING_ACTIVEMQ_BROKER_URL=tcp://host.docker.internal:61616 \
  -e SPRING_MAIL_HOST=host.docker.internal \
  -e SPRING_MAIL_PORT=1025 \
  consumer:latest
```

## Server or prod-style run

The same image can point either to in-stack services or external infrastructure.

In-stack example:

```bash
docker run \
  -p4002:4002 \
  -e SPRING_ACTIVEMQ_BROKER_URL=tcp://activemq:61616 \
  -e SPRING_MAIL_HOST=mailhog \
  -e SPRING_MAIL_PORT=1025 \
  slawekradzyminski/consumer:3.3.6
```

Externalized example:

```bash
docker run \
  -p4002:4002 \
  -e SPRING_ACTIVEMQ_BROKER_URL=tcp://broker.example.com:61616 \
  -e SPRING_ACTIVEMQ_USER=consumer \
  -e SPRING_ACTIVEMQ_PASSWORD=secret \
  -e SPRING_MAIL_HOST=smtp.example.com \
  -e SPRING_MAIL_PORT=587 \
  -e ACTIVEMQ_DESTINATION=email \
  -e EMAIL_FROM=no-reply@example.com \
  slawekradzyminski/consumer:3.3.3
```

## Native image

The published image is a GraalVM native executable built by `Dockerfile` with the `native` Maven profile
(`./mvnw -Pnative -DskipTests package` inside `ghcr.io/graalvm/native-image-community:25`, statically linked
except for glibc, running on `gcr.io/distroless/base-debian13`). `Dockerfile.jvm` keeps the previous Temurin
JVM image as a fallback; the default `./mvnw` build is unchanged and still produces `target/consumer.jar`.

Measured locally (arm64, Artemis 2.55.0, Mailpit 1.30.0, after delivering 1000 emails):

| | Native | JVM (`-Xms192m -Xmx768m`) |
| --- | --- | --- |
| Spring startup | 0.29-0.38 s | 2.1-4.5 s |
| Process RSS (`/proc/1/status` VmRSS) | 46-71 MB, peak 102 MB | ~280 MB |
| Image (compressed / unpacked) | 47 MiB / 203 MB | 125 MiB / 512 MB |

Run the native image with an explicit heap cap and a small container limit, for example
`command: ["-Xmx64m"]` and `mem_limit: 160m`. `JAVA_TOOL_OPTIONS` is ignored by native executables, and the
`blocked.certs` mount at `/opt/java/openjdk/lib/security/` has no effect: the certificate mitigation is applied to
the builder JDK at image build time. GC pause, buffer-pool and JIT metrics are not exported by the native
runtime, and `jvm_memory_max_bytes` reports `-1`.

Reflection, proxy and resource metadata the binary needs:

- `EmailContractRuntimeHints` (imported by `JmsConfig`) registers `EmailDto` and `EmailTemplate` for Jackson
  binding and for `Class.forName` resolution of the producer's fully qualified `_awesome_` type id.
- `src/main/resources/META-INF/native-image/com.awesome.testing/consumer-library-hints/reachability-metadata.json`
  covers the ActiveMQ OpenWire client (transport and wire-format factories, every OpenWire marshaller version,
  URI option setters), Spring JMS connection/session proxies and Angus Mail SMTP. It was curated from a GraalVM
  tracing-agent run against a real broker; Spring AOT owns the `com.awesome.testing/consumer` directory and
  overwrites files placed there.
- `NativeImageHintsTest` guards both on the JVM, and `scripts/native-smoke.sh` proves them in the binary.

Smoke-test any consumer image end to end (Artemis -> consumer -> Mailpit; requires Docker, curl and python3):

```bash
docker build --tag consumer:native .
scripts/native-smoke.sh consumer:native
```

It publishes the contract fixture `src/test/resources/contracts/email-jms-contract.json`, a `GENERIC` message
and an unknown template value, checks in Mailpit that each email arrives with the subject and body the producer
rendered (the unknown template must arrive as a
`GENERIC` email through `EmailTemplate.fromValue`), that no native metadata error is logged, and reports startup time, image size and memory.

## Verification and releases

Pull requests and pushes to `master` run the Maven test suite, build both
containers, and verify email delivery across the complete Artemis -> consumer ->
Mailpit path for each. Run the same checks locally with:

```bash
./mvnw --batch-mode verify
docker build --file Dockerfile.jvm --tag consumer:jvm .
.github/scripts/verify-container.sh consumer:jvm
docker build --tag consumer:native .
scripts/native-smoke.sh consumer:native
```

Container publishing is owned by `.github/workflows/publish-image.yml`. A release
tag such as `v3.3.6` is accepted only when `pom.xml` contains the exact non-snapshot
version `3.3.6`. The workflow smoke-tests the native image, builds it
on an `ubuntu-24.04` (amd64) and an `ubuntu-24.04-arm` (arm64) runner because
native-image does not cross-compile, and publishes one `linux/amd64` and `linux/arm64`
manifest to both:

- `ghcr.io/slawekradzyminski/jms-email-consumer`
- `slawekradzyminski/consumer`

It also publishes a commit-SHA tag, SBOM, provenance, and OCI metadata. A manual
run requires a semantic prerelease such as `3.3.6-rc.1`, publishes that version
and the SHA tag, and only accepts it from the matching `3.3.6-SNAPSHOT` Maven
line on `master`. Stable versions can only be published by a Git tag. GitHub
Actions needs repository secrets named `DOCKERHUB_USERNAME` and
`DOCKERHUB_TOKEN`; GHCR uses `GITHUB_TOKEN`.

The current release source uses the exact version `3.3.6`; tag that verified
commit as `v3.3.6`. After publishing, begin the next development line with
`3.3.6-SNAPSHOT`. Do not reuse or move an existing release tag.

`build-multiarch.sh` remains available only as an emergency local publishing
fallback; it emulates the foreign architecture, which is very slow for the native
build, and `DOCKERFILE=Dockerfile.jvm` publishes the JVM image instead. Normal releases should use GitHub Actions so tests, source revision,
provenance, and the published manifest stay connected.
