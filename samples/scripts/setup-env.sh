#!/usr/bin/env bash
# Sets up the full environment from scratch:
#   kind cluster + Docker registry + Camel K operator + Temporal server
#
# Run once, then use deploy.sh to build and deploy the sample apps.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

setup_environment

log "Environment ready!"
log "Run ./samples/scripts/deploy.sh to build and deploy the sample apps."
