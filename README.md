## Requirements

- Java 25 (Temurin or compatible)
- Docker (optional, for image builds or local Compose usage)

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
./mvnw clean package spring-boot:repackage
SPRING_ACTIVEMQ_BROKER_URL=tcp://localhost:61616 \
SPRING_MAIL_HOST=localhost \
SPRING_MAIL_PORT=1025 \
java -jar target/consumer-1.0.0.jar
```

If you use different credentials or destination names, override the remaining variables as needed.

## Docker or localstack-style run

No extra env vars are required when the app runs in a Compose network where `activemq` and `mailhog` resolve by service name.

```bash
./mvnw clean package spring-boot:repackage
docker build --tag=consumer:latest .
docker run -p4002:4002 consumer:latest
```

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
  slawekradzyminski/consumer:3.3.1
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
  slawekradzyminski/consumer:3.3.1
```

## Publish image

Use the multi-arch helper:

```bash
./build-multiarch.sh 3.3.1
```
