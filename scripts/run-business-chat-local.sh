#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env"
MAVEN_BIN="/Users/admin/Documents/apache-maven-3.9.11/bin/mvn"
MAVEN_SETTINGS="/Users/admin/Documents/apache-maven-3.9.11/conf/settings.xml"
LAB_MIND_LOG_DIR="${ROOT_DIR}/logs/lab-mind-business-chat"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo ".env was not found: ${ENV_FILE}" >&2
  exit 1
fi

set -a
source "${ENV_FILE}"
set +a

export LAB_MIND_LOG_DIR
mkdir -p "${LAB_MIND_LOG_DIR}"

for required_env in \
  LAB_MIND_MINIO_ENDPOINT \
  LAB_MIND_MINIO_ACCESS_KEY \
  LAB_MIND_MINIO_SECRET_KEY \
  LAB_MIND_MINIO_BUCKET \
  LAB_MIND_MODEL_API_CONFIG_AES_KEY_BASE64 \
  LAB_MIND_PAPER_GRAPH_SERVICE_BASE_URL \
  LAB_MIND_PAPER_GRAPH_INTERNAL_API_TOKEN
do
  if [[ -z "${!required_env:-}" ]]; then
    echo "${required_env} must be configured in ${ENV_FILE}" >&2
    exit 1
  fi
done

if ! python3 - <<'PY'
import base64
import os
import sys

value = os.environ["LAB_MIND_MODEL_API_CONFIG_AES_KEY_BASE64"].strip()
try:
    decoded = base64.b64decode(value, validate=True)
except Exception:
    print("LAB_MIND_MODEL_API_CONFIG_AES_KEY_BASE64 must be base64 encoded", file=sys.stderr)
    sys.exit(1)
if len(decoded) not in (16, 24, 32):
    print("LAB_MIND_MODEL_API_CONFIG_AES_KEY_BASE64 must decode to 16, 24, or 32 bytes", file=sys.stderr)
    sys.exit(1)
PY
then
  exit 1
fi

cd "${ROOT_DIR}"
"${MAVEN_BIN}" \
  -s "${MAVEN_SETTINGS}" \
  -Dmaven.repo.local="${ROOT_DIR}/.m2" \
  -pl lab-mind-backend/services/lab-mind-business/lab-mind-business-chat \
  -am \
  -DskipTests \
  package

exec java -jar \
  "${ROOT_DIR}/lab-mind-backend/services/lab-mind-business/lab-mind-business-chat/target/lab-mind-business-chat-1.0-SNAPSHOT.jar"
