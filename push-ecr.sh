#!/bin/bash

set -e

REGION="ap-northeast-2"
ACCOUNT_ID="929368845727"
ECR_REGISTRY="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"
REPOSITORY_NAME="community-backend"
IMAGE_TAG="$1"

if [ -z "$IMAGE_TAG" ]; then
  echo "Usage: ./scripts/push-ecr.sh <image-tag>"
  echo "Example: ./scripts/push-ecr.sh v1.0.0"
  exit 1
fi

IMAGE_URI="${ECR_REGISTRY}/${REPOSITORY_NAME}:${IMAGE_TAG}"

echo "ECR login..."
aws ecr get-login-password --region "${REGION}" \
| docker login --username AWS --password-stdin "${ECR_REGISTRY}"

echo "Build and push image: ${IMAGE_URI}"

docker buildx build \
  --platform linux/amd64 \
  -t "${IMAGE_URI}" \
  --push .

echo "Pushed: ${IMAGE_URI}"