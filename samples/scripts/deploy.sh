#!/usr/bin/env bash
# Builds the component + sample JARs, pushes Docker images to the local
# registry, and deploys the worker + Camel HTTP route via Camel K.
#
# Prerequisites: run setup-env.sh first (or have a kind cluster with Camel K
# and Temporal already running).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

# ── Build ────────────────────────────────────────────────────────────────────

log "Building component JAR"
mvn -q -f "${ROOT_DIR}/pom.xml" -DskipTests clean install

log "Building sample JARs"
mvn -q -f "${SAMPLES_DIR}/pom.xml" clean package

# ── Docker images ────────────────────────────────────────────────────────────

build_and_push_image worker "${WORKER_IMAGE_REPO}" "${IMAGE_TAG}"
build_and_push_image camel-http-temporal "${ROUTE_IMAGE_REPO}" "${IMAGE_TAG}"

WORKER_IMAGE="$(cluster_image_ref "${WORKER_IMAGE_REPO}" "${IMAGE_TAG}")"
ROUTE_IMAGE="$(cluster_image_ref "${ROUTE_IMAGE_REPO}" "${IMAGE_TAG}")"

# ── Deploy to Kubernetes ─────────────────────────────────────────────────────

deploy_worker "${WORKER_IMAGE}"

log "Waiting for worker to register with Temporal"
wait_for_log_pattern "-l app=temporal-worker" "TEMPORAL_WORKER_READY|Started Worker"

deploy_camel_route "${ROUTE_IMAGE}"

# ── Done ─────────────────────────────────────────────────────────────────────

log "Deployment complete!"
echo ""
echo "To access the HTTP API, run:"
echo "  kubectl -n ${NAMESPACE} port-forward svc/camel-http-temporal 8080:8080"
echo ""
echo "Then test with:"
echo "  # Start a workflow"
echo '  curl -s -X POST http://localhost:8080/workflow/start \'
echo '    -H "Content-Type: application/json" -d '"'"'"Alice"'"'"''
echo ""
echo "  # Query workflow status (replace <workflowId> with actual ID)"
echo "  curl -s http://localhost:8080/workflow/<workflowId>/query/getStatus"
echo ""
echo "  # Signal workflow (approve)"
echo '  curl -s -X POST http://localhost:8080/workflow/<workflowId>/signal/approve \'
echo '    -H "Content-Type: application/json" -d '"'"'"manager"'"'"''
