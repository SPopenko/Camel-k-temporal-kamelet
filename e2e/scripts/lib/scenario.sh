#!/usr/bin/env bash

integration_name() {
  local phase="$1"
  local scenario_tag="$2"
  printf 'temporal-%s-%s' "${phase}" "${scenario_tag}"
}

cleanup_integrations() {
  local e2e_namespace="$1"
  shift

  local integration_name
  for integration_name in "$@"; do
    delete_integration_if_present "${e2e_namespace}" "${integration_name}"
  done
}

run_temporal_integration_step() {
  local e2e_namespace="$1"
  local name="$2"
  local image="$3"
  local log_pattern="$4"
  shift 4

  run_integration "${e2e_namespace}" "${name}" "${image}" "$@"
  wait_for_integration_pod "${e2e_namespace}" "${name}"
  wait_for_log_pattern "${e2e_namespace}" "pod/$(integration_pod "${e2e_namespace}" "${name}")" "${log_pattern}"
}

run_temporal_e2e_scenario() {
  local e2e_namespace="$1"
  local route_image="$2"
  local workflow_id="$3"
  local scenario_tag="$4"
  local payload="$5"
  local signal_payload="$6"

  local start_name
  local query_before_name
  local signal_name
  local query_after_name

  start_name="$(integration_name start "${scenario_tag}")"
  query_before_name="$(integration_name query-before "${scenario_tag}")"
  signal_name="$(integration_name signal "${scenario_tag}")"
  query_after_name="$(integration_name query-after "${scenario_tag}")"

  run_temporal_integration_step "${e2e_namespace}" "${start_name}" "${route_image}" \
    "TEMPORAL_START workflowId=${workflow_id}" \
    -e "APP_OPERATION=start" \
    -e "APP_WORKFLOW_ID=${workflow_id}" \
    -e "APP_PAYLOAD=${payload}"

  run_temporal_integration_step "${e2e_namespace}" "${query_before_name}" "${route_image}" \
    "TEMPORAL_QUERY workflowId=${workflow_id} status=(AWAITING_APPROVAL|PENDING)" \
    -e "APP_OPERATION=query" \
    -e "APP_WORKFLOW_ID=${workflow_id}"

  run_temporal_integration_step "${e2e_namespace}" "${signal_name}" "${route_image}" \
    "TEMPORAL_SIGNAL workflowId=${workflow_id} signal=approve" \
    -e "APP_OPERATION=signal" \
    -e "APP_WORKFLOW_ID=${workflow_id}" \
    -e "APP_SIGNAL_PAYLOAD=${signal_payload}"

  run_temporal_integration_step "${e2e_namespace}" "${query_after_name}" "${route_image}" \
    "TEMPORAL_QUERY workflowId=${workflow_id} status=(APPROVED|COMPLETED)" \
    -e "APP_OPERATION=query" \
    -e "APP_WORKFLOW_ID=${workflow_id}"

  wait_for_log_pattern "${e2e_namespace}" \
    "deployment/temporal-worker" \
    "WORKFLOW_RESULT workflowId=${workflow_id} result=Hello, ${payload}! Approved by: ${signal_payload}"

  log "Camel K e2e scenario passed for workflow ${workflow_id}"
}
