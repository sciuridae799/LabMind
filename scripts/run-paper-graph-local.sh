#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVICE_DIR="${ROOT_DIR}/paper-graph-service"
ENV_FILE="${SERVICE_DIR}/.env"
PYTHON_BIN="${SERVICE_DIR}/.venv/bin/python"
MODE="${1:-}"

if [[ "${MODE}" != "api" && "${MODE}" != "worker" ]]; then
  echo "usage: $0 api|worker" >&2
  exit 1
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  echo ".env was not found: ${ENV_FILE}" >&2
  exit 1
fi

if [[ ! -x "${PYTHON_BIN}" ]]; then
  echo "Python virtual environment was not found: ${PYTHON_BIN}" >&2
  exit 1
fi

set -a
source "${ENV_FILE}"
set +a

required_env=(
  LAB_MIND_PAPER_GRAPH_POSTGRES_DSN
  LAB_MIND_MINIO_ENDPOINT
  LAB_MIND_MINIO_ACCESS_KEY
  LAB_MIND_MINIO_SECRET_KEY
  LAB_MIND_MINIO_BUCKET
  LAB_MIND_KAFKA_BOOTSTRAP_SERVERS
  LAB_MIND_KAFKA_SECURITY_PROTOCOL
  LAB_MIND_PAPER_GRAPH_LLM_CHAT_COMPLETIONS_URL
  LAB_MIND_PAPER_GRAPH_LLM_API_KEY
  LAB_MIND_PAPER_GRAPH_LLM_MODEL
  LAB_MIND_PAPER_GRAPH_INTERNAL_API_TOKEN
)

if [[ "${LAB_MIND_KAFKA_SECURITY_PROTOCOL}" == SASL_* ]]; then
  required_env+=(
    LAB_MIND_KAFKA_SASL_MECHANISM
    LAB_MIND_KAFKA_USERNAME
    LAB_MIND_KAFKA_PASSWORD
  )
fi

if [[ "${MODE}" == "api" ]]; then
  required_env+=(LAB_MIND_PAPER_GRAPH_API_HOST LAB_MIND_PAPER_GRAPH_API_PORT)
fi

for name in "${required_env[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    echo "${name} must be configured in ${ENV_FILE}" >&2
    exit 1
  fi
done

cd "${SERVICE_DIR}"
if [[ "${MODE}" == "api" ]]; then
  exec "${PYTHON_BIN}" -m uvicorn app.main:app \
    --host "${LAB_MIND_PAPER_GRAPH_API_HOST}" \
    --port "${LAB_MIND_PAPER_GRAPH_API_PORT}"
fi

exec "${PYTHON_BIN}" -m app.worker
