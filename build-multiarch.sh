#!/bin/bash

set -euo pipefail

if [ $# -ne 1 ]; then
  echo "Usage: $0 <version>"
  exit 1
fi

VERSION="$1"
# The default Dockerfile builds the GraalVM native executable; under QEMU the foreign architecture takes a
# very long time. DOCKERFILE=Dockerfile.jvm publishes the JVM fallback image instead.
DOCKERFILE="${DOCKERFILE:-Dockerfile}"
PROJECT_VERSION="$(./mvnw --batch-mode -Dstyle.color=never help:evaluate \
  -Dexpression=project.version -q -DforceStdout)"

if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Version must use MAJOR.MINOR.PATCH: $VERSION" >&2
  exit 1
fi

if [[ "$PROJECT_VERSION" == *-SNAPSHOT || "$PROJECT_VERSION" != "$VERSION" ]]; then
  echo "pom.xml must contain the exact non-snapshot version $VERSION; found $PROJECT_VERSION" >&2
  exit 1
fi

echo "Setting up Docker buildx..."
docker buildx create --name jms-email-consumer-release --use || \
  docker buildx use jms-email-consumer-release
docker buildx inspect --bootstrap

echo "Building and pushing multi-architecture image..."
docker buildx build --platform linux/amd64,linux/arm64 \
  --file "$DOCKERFILE" \
  -t slawekradzyminski/consumer:"$VERSION" \
  --push \
  .

echo "Multi-architecture image built and pushed successfully!"
echo "Verify the image with: docker buildx imagetools inspect slawekradzyminski/consumer:$VERSION"
