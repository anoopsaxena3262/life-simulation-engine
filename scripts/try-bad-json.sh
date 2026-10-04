#!/usr/bin/env bash
# A body that is not JSON. Spring's title, not the validation handler.
# Service must already be running. See DEVELOPER.md.

set -euo pipefail
source "$(dirname "$0")/common.sh"
demo_init

demo_request "not json" POST "/api/v1/boards" 400 '{'
demo_expect_field title "Bad Request"
demo_done
