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
  local operator_namespace="$1"
  local e2e_namespace="$2"

  kubectl get namespace "${operator_namespace}" >/dev/null 2>&1 || kubectl create namespace "${operator_namespace}"
  kubectl get namespace "${e2e_namespace}" >/dev/null 2>&1 || kubectl create namespace "${e2e_namespace}"
}

install_camel_k_operator() {
  local operator_namespace="$1"

  log "Installing Camel K operator ${CAMEL_K_VERSION}"
  kubectl apply -k "github.com/apache/camel-k/install/overlays/kubernetes/descoped?ref=${CAMEL_K_VERSION}" --server-side
  kubectl rollout status deployment/camel-k-operator -n "${operator_namespace}" --timeout=300s
}

apply_integration_platform() {
  local e2e_namespace="$1"
  local registry_address="$2"
  local registry_org="$3"

  log "Applying IntegrationPlatform in ${e2e_namespace}"
  kubectl apply -f - <<EOF
apiVersion: camel.apache.org/v1
kind: IntegrationPlatform
metadata:
  name: camel-k
  namespace: ${e2e_namespace}
spec:
  build:
    registry:
      address: ${registry_address}
      organization: ${registry_org}
      insecure: true
EOF

  kubectl wait --for=jsonpath='{.status.phase}'=Ready integrationplatform/camel-k -n "${e2e_namespace}" --timeout=300s
}

apply_temporal_stack() {
  local e2e_namespace="$1"

  log "Deploying Temporal services in ${e2e_namespace}"
  kubectl apply -n "${e2e_namespace}" -f "${K8S_DIR}/temporal.yaml"
  kubectl rollout status deployment/temporal-postgres -n "${e2e_namespace}" --timeout=300s
  kubectl rollout status deployment/temporal-frontend -n "${e2e_namespace}" --timeout=300s
}

apply_kamelets() {
  local e2e_namespace="$1"

  log "Applying Kamelet definitions in ${e2e_namespace}"
  kubectl apply -n "${e2e_namespace}" -f "${ROOT_DIR}/src/main/resources/kamelets"
}

setup_environment() {
  require_cmd docker
  require_cmd kubectl
  require_cmd curl
  require_cmd "${KAMEL_BIN}"

  ensure_registry
  ensure_kind_cluster
  ensure_namespaces "${OPERATOR_NAMESPACE}" "${E2E_NAMESPACE}"
  install_camel_k_operator "${OPERATOR_NAMESPACE}"
  apply_integration_platform "${E2E_NAMESPACE}" "${REGISTRY_HOST_CLUSTER}" "${REGISTRY_ORG}"
  apply_temporal_stack "${E2E_NAMESPACE}"
  apply_kamelets "${E2E_NAMESPACE}"
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
