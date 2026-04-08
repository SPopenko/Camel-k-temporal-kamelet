#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=lib/base.sh
source "${SCRIPT_DIR}/lib/base.sh"
# shellcheck source=lib/cluster.sh
source "${SCRIPT_DIR}/lib/cluster.sh"
# shellcheck source=lib/images.sh
source "${SCRIPT_DIR}/lib/images.sh"
# shellcheck source=lib/deploy.sh
source "${SCRIPT_DIR}/lib/deploy.sh"
