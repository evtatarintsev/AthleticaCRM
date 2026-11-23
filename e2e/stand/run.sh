#!/usr/bin/env bash
# Полный прогон e2e: сборка образов из рабочей копии → стенд → Playwright → удаление стенда.
#   e2e/stand/run.sh [--keep] [аргументы playwright test]
# --keep оставляет стенд поднятым; повторный запуск с ним пересобирает только изменившиеся образы.
set -euo pipefail

cd "$(dirname "$0")/../.."

keep=0
if [[ "${1:-}" == "--keep" ]]; then
  keep=1
  shift
fi

compose=(docker compose -p athletica-e2e
  -f docker-compose.prod.yaml -f e2e/stand/docker-compose.e2e.yaml
  --env-file e2e/stand/e2e.env --profile test)

cleanup() {
  local status=$?
  if [[ $status -ne 0 ]]; then
    "${compose[@]}" logs --no-color --tail=200 server nginx mailpit || true
    echo "Отчёт Playwright: e2e/playwright-report/index.html" >&2
  fi
  if [[ $keep -eq 0 ]]; then
    "${compose[@]}" down -v --remove-orphans
  fi
  exit "$status"
}
trap cleanup EXIT

e2e/stand/certs.sh
if [[ ! -d e2e/node_modules ]]; then
  (cd e2e && npm ci --no-audit --no-fund)
fi

"${compose[@]}" up -d --wait --build postgres rustfs mailpit server frontend nginx
"${compose[@]}" run --rm playwright "$@"
