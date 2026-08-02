#!/usr/bin/env bash
# Bootstraps the S3-compatible object storage used by product-catalog.
# Run after `docker compose -f docker/docker-compose.yaml up -d localstack`.
#
# Requires the AWS CLI (https://aws.amazon.com/cli/) pointed at LocalStack.
set -euo pipefail

ENDPOINT="${S3_ENDPOINT_OVERRIDE:-http://localhost:4566}"
BUCKET="${S3_BUCKET:-product-images}"

echo "Bootstrapping bucket '${BUCKET}' on ${ENDPOINT} ..."

aws --endpoint-url="${ENDPOINT}" s3 mb "s3://${BUCKET}" 2>/dev/null || echo "Bucket '${BUCKET}' already exists."

aws --endpoint-url="${ENDPOINT}" s3api put-bucket-policy --bucket "${BUCKET}" --policy "{
  \"Version\": \"2012-10-17\",
  \"Statement\": [
    {
      \"Effect\": \"Allow\",
      \"Principal\": \"*\",
      \"Action\": [\"s3:GetObject\"],
      \"Resource\": \"arn:aws:s3:::${BUCKET}/*\"
    }
  ]
}"

echo "Done. Bucket '${BUCKET}' is publicly readable."
