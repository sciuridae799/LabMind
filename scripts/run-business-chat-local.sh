#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env"
MAVEN_BIN="/Users/admin/Documents/apache-maven-3.9.11/bin/mvn"
MAVEN_SETTINGS="/Users/admin/Documents/apache-maven-3.9.11/conf/settings.xml"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo ".env was not found: ${ENV_FILE}" >&2
  exit 1
fi

set -a
source "${ENV_FILE}"
set +a

for required_env in \
  SUPER_AGENT_MINIO_ENDPOINT \
  SUPER_AGENT_MINIO_ACCESS_KEY \
  SUPER_AGENT_MINIO_SECRET_KEY \
  SUPER_AGENT_MINIO_BUCKET \
  SUPER_AGENT_MODEL_API_CONFIG_AES_KEY_BASE64
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

value = os.environ["SUPER_AGENT_MODEL_API_CONFIG_AES_KEY_BASE64"].strip()
try:
    decoded = base64.b64decode(value, validate=True)
except Exception:
    print("SUPER_AGENT_MODEL_API_CONFIG_AES_KEY_BASE64 must be base64 encoded", file=sys.stderr)
    sys.exit(1)
if len(decoded) not in (16, 24, 32):
    print("SUPER_AGENT_MODEL_API_CONFIG_AES_KEY_BASE64 must decode to 16, 24, or 32 bytes", file=sys.stderr)
    sys.exit(1)
PY
then
  exit 1
fi

cd "${ROOT_DIR}/super-agent-backend"
exec "${MAVEN_BIN}" \
  -s "${MAVEN_SETTINGS}" \
  -Dmaven.repo.local="${ROOT_DIR}/.m2" \
  -pl services/super-agent-business/super-agent-business-chat \
  -am \
  spring-boot:run
