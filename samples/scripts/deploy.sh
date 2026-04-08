#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SAMPLES_DIR="$PROJECT_ROOT/samples"
NAMESPACE="camel-temporal-sample"

log() { echo "==> $*"; }

# ── Build ────────────────────────────────────────────────────────────────────

log "Building component JAR"
mvn -q -f "$PROJECT_ROOT/pom.xml" -DskipTests clean install

log "Building sample JARs"
mvn -q -f "$SAMPLES_DIR/pom.xml" clean package

# ── Docker images ────────────────────────────────────────────────────────────

log "Building worker image"
docker build -t camel-temporal-sample/worker:latest \
  --build-arg APP_MODULE=worker \
  -f "$SAMPLES_DIR/Dockerfile" "$PROJECT_ROOT"

log "Building camel-http-temporal image"
docker build -t camel-temporal-sample/camel-http-temporal:latest \
  --build-arg APP_MODULE=camel-http-temporal \
  -f "$SAMPLES_DIR/Dockerfile" "$PROJECT_ROOT"

# ── Load into kind ───────────────────────────────────────────────────────────

log "Loading images into kind cluster"
kind load docker-image camel-temporal-sample/worker:latest
kind load docker-image camel-temporal-sample/camel-http-temporal:latest

# ── Deploy to Kubernetes ─────────────────────────────────────────────────────

log "Deploying Temporal server"
kubectl apply -f "$SAMPLES_DIR/k8s/temporal.yaml"

log "Waiting for Temporal frontend to be ready"
kubectl -n "$NAMESPACE" rollout status deployment/temporal-postgres --timeout=120s
kubectl -n "$NAMESPACE" rollout status deployment/temporal-frontend --timeout=120s

log "Deploying demo worker"
kubectl apply -f "$SAMPLES_DIR/k8s/worker-deployment.yaml"

log "Deploying Camel HTTP app"
kubectl apply -f "$SAMPLES_DIR/k8s/camel-http-temporal-deployment.yaml"

log "Waiting for all pods to be ready"
kubectl -n "$NAMESPACE" rollout status deployment/demo-worker --timeout=120s
kubectl -n "$NAMESPACE" rollout status deployment/camel-http-temporal --timeout=120s

# ── Done ─────────────────────────────────────────────────────────────────────

log "Deployment complete!"
echo ""
echo "To access the HTTP API, run:"
echo "  kubectl -n $NAMESPACE port-forward svc/camel-http-temporal 8080:8080"
echo ""
echo "Then test with:"
echo "  # Start a workflow"
echo '  curl -s -X POST http://localhost:8080/workflow/start -H "Content-Type: application/json" -d '"'"'"Alice"'"'"''
echo ""
echo "  # Query workflow status (replace <workflowId> with actual ID)"
echo "  curl -s http://localhost:8080/workflow/<workflowId>/query/getStatus"
echo ""
echo "  # Signal workflow (approve)"
echo '  curl -s -X POST http://localhost:8080/workflow/<workflowId>/signal/approve -H "Content-Type: application/json" -d '"'"'"manager"'"'"''
