#!/usr/bin/env bash

set -euo pipefail

SCRIPT_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_LIB_DIR}/../../.." && pwd)"
SAMPLES_DIR="${ROOT_DIR}/samples"
K8S_DIR="${SAMPLES_DIR}/k8s"
BIN_DIR="${SAMPLES_DIR}/bin"

KIND_VERSION="${KIND_VERSION:-v0.23.0}"
KIND_CLUSTER_NAME="${KIND_CLUSTER_NAME:-camel-temporal-sample}"
KIND_CONFIG="${K8S_DIR}/kind-config.yaml"

CAMEL_K_VERSION="${CAMEL_K_VERSION:-v2.8.0}"
OPERATOR_NAMESPACE="${OPERATOR_NAMESPACE:-camel-k}"
NAMESPACE="${NAMESPACE:-camel-temporal-sample}"

REGISTRY_CONTAINER="${REGISTRY_CONTAINER:-camel-temporal-sample-registry}"
REGISTRY_PORT="${REGISTRY_PORT:-5001}"
REGISTRY_HOST_DOCKER="localhost:${REGISTRY_PORT}"
REGISTRY_HOST_CLUSTER="host.docker.internal:${REGISTRY_PORT}"
REGISTRY_ORG="${REGISTRY_ORG:-camel-temporal-sample}"

IMAGE_TAG="${IMAGE_TAG:-$(date +%Y%m%d%H%M%S)}"
ROUTE_IMAGE_REPO="${REGISTRY_ORG}/camel-http-temporal"
WORKER_IMAGE_REPO="${REGISTRY_ORG}/worker"

KIND_BIN="${BIN_DIR}/kind"
KAMEL_BIN="${KAMEL_BIN:-kamel}"

log() {
  printf '[sample] %s\n' "$*"
}

require_cmd() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "Missing required command: ${cmd}" >&2
    exit 1
  fi
}

ensure_dirs() {
  mkdir -p "${BIN_DIR}"
}

ensure_kind_binary() {
  ensure_dirs

  if [[ -x "${KIND_BIN}" ]]; then
    return
  fi

  local os arch url
  os="$(uname | tr '[:upper:]' '[:lower:]')"
  arch="$(uname -m)"

  case "${arch}" in
    x86_64) arch="amd64" ;;
    arm64|aarch64) arch="arm64" ;;
    *)
      echo "Unsupported architecture for kind download: ${arch}" >&2
      exit 1
      ;;
  esac

  url="https://kind.sigs.k8s.io/dl/${KIND_VERSION}/kind-${os}-${arch}"
  log "Downloading kind ${KIND_VERSION} to ${KIND_BIN}"
  curl -fsSL "${url}" -o "${KIND_BIN}"
  chmod +x "${KIND_BIN}"
}

kind_cmd() {
  "${KIND_BIN}" "$@"
}
