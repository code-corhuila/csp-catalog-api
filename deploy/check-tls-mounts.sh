#!/usr/bin/env bash
# Checks that deploy/compose.yml mounts the TLS files of ADR-067 into the container, read-only, and refuses to render without them.
# It needs Docker Compose only: nothing is started. Run it from anywhere: bash deploy/check-tls-mounts.sh
# It prints the totals and exits with 1 when a check fails, so a pass is not an "OK" without counting.
set -u
export MSYS_NO_PATHCONV=1   # Git Bash on Windows would rewrite /host/... into a path of its own
HERE="$(cd "$(dirname "$0")" && { pwd -W 2>/dev/null || pwd; })"   # pwd -W: the Windows form of the path, for docker.exe
COMPOSE="$HERE/compose.yml"
PASSED=0
FAILED=0

# The variables compose needs besides the three TLS host files; the values are placeholders of a throwaway check.
base_env() {
  echo "CATALOG_APP_PASSWORD=check-only"
}

render() { # render <variables to set...>: the rendered compose, or the error of compose
  (
    # Whatever the shell already exports must not leak into the check: only the variables given here are set.
    unset CATALOG_TLS_CERTIFICATE_HOST_FILE CATALOG_TLS_PRIVATE_KEY_HOST_FILE CATALOG_TLS_CA_HOST_FILE CATALOG_APP_PASSWORD
    export $(base_env) "$@"
    docker compose -f "$COMPOSE" config 2>&1
  )
}

check() { # check <description> <command...>
  local description="$1"; shift
  if "$@"; then
    PASSED=$((PASSED + 1)); echo "ok   $description"
  else
    FAILED=$((FAILED + 1)); echo "FAIL $description"
  fi
}

ALL="CATALOG_TLS_CERTIFICATE_HOST_FILE=/host/catalog.crt CATALOG_TLS_PRIVATE_KEY_HOST_FILE=/host/catalog.key CATALOG_TLS_CA_HOST_FILE=/host/ca.crt"
RENDERED="$(render $ALL)"

has() { grep -q -- "$1" <<<"$RENDERED"; }
count_is() { [ "$(grep -c -- "$1" <<<"$RENDERED")" = "$2" ]; }
refuses_without() { # the variable that is left out
  local out
  out="$(render $(tr ' ' '\n' <<<"$ALL" | grep -v "^$1=") )"
  grep -q -- "$1" <<<"$out" && ! grep -q -- "services:" <<<"$out"
}

check "the compose file renders with the three host files set" has "name: csp-catalog-api"
check "the certificate is mounted at /run/tls/catalog.crt" has "target: /run/tls/catalog.crt"
check "the private key is mounted at /run/tls/catalog.key" has "target: /run/tls/catalog.key"
check "the CA is mounted at /run/tls/ca.crt" has "target: /run/tls/ca.crt"
check "the three mounts are read-only" count_is "read_only: true" 3
check "the host certificate is the source of its mount" has "source: .*/host/catalog.crt$"
check "the host private key is the source of its mount" has "source: .*/host/catalog.key$"
check "the host CA is the source of its mount" has "source: .*/host/ca.crt$"
check "the service reads the certificate from /run/tls/catalog.crt" has "CATALOG_TLS_CERTIFICATE_FILE: /run/tls/catalog.crt"
check "the service reads the key from /run/tls/catalog.key" has "CATALOG_TLS_PRIVATE_KEY_FILE: /run/tls/catalog.key"
check "the healthcheck reads the CA from /run/tls/ca.crt" has "CATALOG_TLS_CA_FILE: /run/tls/ca.crt"
check "compose refuses to render without CATALOG_TLS_CERTIFICATE_HOST_FILE" refuses_without CATALOG_TLS_CERTIFICATE_HOST_FILE
check "compose refuses to render without CATALOG_TLS_PRIVATE_KEY_HOST_FILE" refuses_without CATALOG_TLS_PRIVATE_KEY_HOST_FILE
check "compose refuses to render without CATALOG_TLS_CA_HOST_FILE" refuses_without CATALOG_TLS_CA_HOST_FILE

echo "checks: $((PASSED + FAILED)), passed: $PASSED, failed: $FAILED"
[ "$FAILED" = 0 ]
