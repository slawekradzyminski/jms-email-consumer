#!/bin/bash

set -euo pipefail

if [ $# -lt 1 ]; then
  echo "Usage: $0 <version>"
  exit 1
fi

VERSION="$1"

echo "Setting up Docker buildx..."
docker buildx create --name mybuilder --use || true
docker buildx inspect --bootstrap

echo "Building and pushing multi-architecture image..."
docker buildx build --platform linux/amd64,linux/arm64 \
  -t slawekradzyminski/consumer:"$VERSION" \
  --push \
  .

echo "Multi-architecture image built and pushed successfully!"
echo "Verify the image with: docker buildx imagetools inspect slawekradzyminski/consumer:$VERSION"
