#!/usr/bin/env bash
# Tears down the sample environment: deletes the kind cluster and Docker registry.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

teardown_environment

log "Environment cleaned up."
