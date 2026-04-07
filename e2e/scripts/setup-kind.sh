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

log "kind + Camel K environment is ready"
log "Route image: ${ROUTE_IMAGE}"
log "Worker image: ${WORKER_IMAGE}"
