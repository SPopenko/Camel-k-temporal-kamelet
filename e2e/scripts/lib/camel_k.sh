#!/usr/bin/env bash

temporal_target_for_namespace() {
  local e2e_namespace="$1"
  printf 'dns:///temporal-frontend.%s.svc.cluster.local:7233' "${e2e_namespace}"
}

deploy_worker() {
  local e2e_namespace="$1"
  local image="$2"
  local temporal_target="$3"
  local temporal_namespace="$4"
  local task_queue="$5"

  log "Deploying Temporal worker ${image}"
  kubectl apply -f - <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: temporal-worker
  namespace: ${e2e_namespace}
spec:
  replicas: 1
  selector:
    matchLabels:
      app: temporal-worker
  template:
    metadata:
      labels:
        app: temporal-worker
    spec:
      containers:
        - name: temporal-worker
          image: ${image}
          imagePullPolicy: Always
          env:
            - name: TEMPORAL_TARGET
              value: ${temporal_target}
            - name: TEMPORAL_NAMESPACE
              value: ${temporal_namespace}
            - name: TEMPORAL_TASK_QUEUE
              value: ${task_queue}
EOF

  kubectl rollout status deployment/temporal-worker -n "${e2e_namespace}" --timeout=300s
  kubectl wait --for=condition=available deployment/temporal-worker -n "${e2e_namespace}" --timeout=300s
}

run_integration() {
  local e2e_namespace="$1"
  local name="$2"
  local image="$3"
  shift 3

  log "Running Camel K integration ${name}"
  "${KAMEL_BIN}" run \
    --name "${name}" \
    --namespace "${e2e_namespace}" \
    --image "${image}" \
    --wait \
    "$@"
}

integration_pod() {
  local e2e_namespace="$1"
  local name="$2"
  kubectl get pods -n "${e2e_namespace}" -l "camel.apache.org/integration=${name}" -o jsonpath='{.items[0].metadata.name}'
}

wait_for_integration_pod() {
  local e2e_namespace="$1"
  local name="$2"
  local deadline=$((SECONDS + 300))

  while (( SECONDS < deadline )); do
    local pod
    pod="$(integration_pod "${e2e_namespace}" "${name}" 2>/dev/null || true)"
    if [[ -n "${pod}" ]]; then
      kubectl wait --for=condition=Ready "pod/${pod}" -n "${e2e_namespace}" --timeout=180s >/dev/null
      return 0
    fi
    sleep 2
  done

  echo "Timed out waiting for integration pod ${name}" >&2
  return 1
}

wait_for_log_pattern() {
  local e2e_namespace="$1"
  local selector="$2"
  local pattern="$3"
  local deadline=$((SECONDS + 300))

  while (( SECONDS < deadline )); do
    if kubectl logs -n "${e2e_namespace}" ${selector} 2>/dev/null | grep -E -q "${pattern}"; then
      return 0
    fi
    sleep 5
  done

  echo "Timed out waiting for log pattern '${pattern}' in ${selector}" >&2
  kubectl logs -n "${e2e_namespace}" ${selector} || true
  return 1
}

delete_integration_if_present() {
  local e2e_namespace="$1"
  local name="$2"

  if kubectl get integration "${name}" -n "${e2e_namespace}" >/dev/null 2>&1; then
    "${KAMEL_BIN}" delete "${name}" -n "${e2e_namespace}"
    kubectl wait --for=delete integration/"${name}" -n "${e2e_namespace}" --timeout=180s || true
  fi
}
