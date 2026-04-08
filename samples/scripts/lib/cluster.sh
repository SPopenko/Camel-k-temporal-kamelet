#!/usr/bin/env bash

ensure_registry() {
  if docker ps --format '{{.Names}}' | grep -qx "${REGISTRY_CONTAINER}"; then
    return
  fi

  if docker ps -a --format '{{.Names}}' | grep -qx "${REGISTRY_CONTAINER}"; then
    log "Starting existing registry container ${REGISTRY_CONTAINER}"
    docker start "${REGISTRY_CONTAINER}" >/dev/null
    return
  fi

  log "Creating local Docker registry on ${REGISTRY_HOST_DOCKER}"
  docker run -d --restart=always -p "${REGISTRY_PORT}:5000" --name "${REGISTRY_CONTAINER}" registry:2 >/dev/null
}

ensure_kind_cluster() {
  ensure_kind_binary

  if kind_cmd get clusters | grep -qx "${KIND_CLUSTER_NAME}"; then
    log "Using existing kind cluster ${KIND_CLUSTER_NAME}"
    return
  fi

  log "Creating kind cluster ${KIND_CLUSTER_NAME}"
  kind_cmd create cluster --name "${KIND_CLUSTER_NAME}" --config "${KIND_CONFIG}"
}

ensure_namespaces() {
  kubectl get namespace "${OPERATOR_NAMESPACE}" >/dev/null 2>&1 || kubectl create namespace "${OPERATOR_NAMESPACE}"
  kubectl get namespace "${NAMESPACE}" >/dev/null 2>&1 || kubectl create namespace "${NAMESPACE}"
}

install_camel_k_operator() {
  log "Installing Camel K operator ${CAMEL_K_VERSION}"
  kubectl apply -k "github.com/apache/camel-k/install/overlays/kubernetes/descoped?ref=${CAMEL_K_VERSION}" --server-side
  kubectl rollout status deployment/camel-k-operator -n "${OPERATOR_NAMESPACE}" --timeout=300s
}

apply_integration_platform() {
  log "Applying IntegrationPlatform in ${NAMESPACE}"
  kubectl apply -f - <<EOF
apiVersion: camel.apache.org/v1
kind: IntegrationPlatform
metadata:
  name: camel-k
  namespace: ${NAMESPACE}
spec:
  build:
    registry:
      address: ${REGISTRY_HOST_CLUSTER}
      organization: ${REGISTRY_ORG}
      insecure: true
EOF

  kubectl wait --for=jsonpath='{.status.phase}'=Ready integrationplatform/camel-k -n "${NAMESPACE}" --timeout=300s
}

apply_temporal_stack() {
  log "Deploying Temporal services in ${NAMESPACE}"
  kubectl apply -n "${NAMESPACE}" -f "${K8S_DIR}/temporal.yaml"
  kubectl rollout status deployment/temporal-postgres -n "${NAMESPACE}" --timeout=300s
  kubectl rollout status deployment/temporal-frontend -n "${NAMESPACE}" --timeout=300s
}

setup_environment() {
  require_cmd docker
  require_cmd kubectl
  require_cmd curl
  require_cmd "${KAMEL_BIN}"

  ensure_registry
  ensure_kind_cluster
  ensure_namespaces
  install_camel_k_operator
  apply_integration_platform
  apply_temporal_stack
}

teardown_environment() {
  if [[ -x "${KIND_BIN}" ]] && kind_cmd get clusters | grep -qx "${KIND_CLUSTER_NAME}"; then
    log "Deleting kind cluster ${KIND_CLUSTER_NAME}"
    kind_cmd delete cluster --name "${KIND_CLUSTER_NAME}"
  fi

  if docker ps -a --format '{{.Names}}' | grep -qx "${REGISTRY_CONTAINER}"; then
    log "Removing local registry ${REGISTRY_CONTAINER}"
    docker rm -f "${REGISTRY_CONTAINER}" >/dev/null
  fi
}
