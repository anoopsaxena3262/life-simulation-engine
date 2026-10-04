#!/usr/bin/env bash
# More cells than game-of-life.max-cells. The grid matches the declared size,
# so this is the cell cap, not a dimension mismatch.
# Service must already be running. See DEVELOPER.md.

set -euo pipefail
source "$(dirname "$0")/common.sh"
demo_init

json=$(python3 - <<'PY'
import json
width = 301
height = 301
row = [False] * width
print(json.dumps({"width": width, "height": height, "cells": [row] * height}))
PY
)
demo_request "301x301 is over max-cells" POST "/api/v1/boards" 400 "$json"
demo_expect_field title "Invalid board"
demo_done
