#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

setup_environment

build_and_push_image route-runner "${ROUTE_IMAGE_REPO}" "${IMAGE_TAG}"
build_and_push_image worker "${WORKER_IMAGE_REPO}" "${IMAGE_TAG}"

ROUTE_IMAGE="$(cluster_image_ref "${ROUTE_IMAGE_REPO}" "${IMAGE_TAG}")"
WORKER_IMAGE="$(cluster_image_ref "${WORKER_IMAGE_REPO}" "${IMAGE_TAG}")"

deploy_worker "${E2E_NAMESPACE}" "${WORKER_IMAGE}" "$(temporal_target_for_namespace "${E2E_NAMESPACE}")" "default" "greetings"
wait_for_log_pattern "${E2E_NAMESPACE}" "deployment/temporal-worker" "TEMPORAL_WORKER_READY"

WORKFLOW_ID="${WORKFLOW_ID:-kamel-e2e-$(date +%s)}"
START_NAME="$(integration_name start "${IMAGE_TAG}")"
QUERY_BEFORE_NAME="$(integration_name query-before "${IMAGE_TAG}")"
SIGNAL_NAME="$(integration_name signal "${IMAGE_TAG}")"
QUERY_AFTER_NAME="$(integration_name query-after "${IMAGE_TAG}")"

cleanup() {
  if [[ "${KEEP_RESOURCES:-0}" != "1" ]]; then
    cleanup_integrations "${E2E_NAMESPACE}" "${START_NAME}" "${QUERY_BEFORE_NAME}" "${SIGNAL_NAME}" "${QUERY_AFTER_NAME}"
  fi
}
trap cleanup EXIT

cleanup_integrations "${E2E_NAMESPACE}" "${START_NAME}" "${QUERY_BEFORE_NAME}" "${SIGNAL_NAME}" "${QUERY_AFTER_NAME}"

run_temporal_e2e_scenario "${E2E_NAMESPACE}" "${ROUTE_IMAGE}" "${WORKFLOW_ID}" "${IMAGE_TAG}" "Alice" "manager-kamelk"
