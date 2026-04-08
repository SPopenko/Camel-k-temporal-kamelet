#!/usr/bin/env bash

temporal_target_for_namespace() {
  printf 'dns:///temporal-frontend.%s.svc.cluster.local:7233' "${NAMESPACE}"
}

deploy_worker() {
  local image="$1"
  local temporal_target
  temporal_target="$(temporal_target_for_namespace)"

  log "Deploying Temporal worker ${image}"
  kubectl apply -f - <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: temporal-worker
  namespace: ${NAMESPACE}
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
              value: default
            - name: TEMPORAL_TASK_QUEUE
              value: greetings
EOF

  kubectl rollout status deployment/temporal-worker -n "${NAMESPACE}" --timeout=300s
}

deploy_camel_route() {
  local image="$1"

  log "Deploying Camel K integration (self-managed image)"
  "${KAMEL_BIN}" run \
    --name camel-http-temporal \
    --namespace "${NAMESPACE}" \
    --env "TEMPORAL_HOST=temporal-frontend.${NAMESPACE}.svc.cluster.local" \
    --env "TEMPORAL_PORT=7233" \
    --env "TEMPORAL_NAMESPACE=default" \
    --env "TEMPORAL_TASK_QUEUE=greetings" \
    --image "${image}"

  kubectl apply -f - <<EOF
apiVersion: v1
kind: Service
metadata:
  name: camel-http-temporal
  namespace: ${NAMESPACE}
spec:
  selector:
    camel.apache.org/integration: camel-http-temporal
  ports:
    - name: http
      port: 8080
      targetPort: 8080
EOF

  kubectl rollout status deployment/camel-http-temporal -n "${NAMESPACE}" --timeout=300s
}

wait_for_log_pattern() {
  local selector="$1"
  local pattern="$2"
  local deadline=$((SECONDS + 300))

  while (( SECONDS < deadline )); do
    if kubectl logs -n "${NAMESPACE}" ${selector} 2>/dev/null | grep -E -q "${pattern}"; then
      return 0
    fi
    sleep 5
  done

  echo "Timed out waiting for log pattern '${pattern}' in ${selector}" >&2
  kubectl logs -n "${NAMESPACE}" ${selector} || true
  return 1
}
