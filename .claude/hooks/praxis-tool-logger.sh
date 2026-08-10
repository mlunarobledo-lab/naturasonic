#!/usr/bin/env bash
# praxis-tool-logger.sh
# Hook PostToolUse para Praxis: append de una linea por herramienta ejecutada.
# No bloquea la ejecucion — siempre devuelve {} a stdout.

set -u

LOG_DIR="${CLAUDE_PROJECT_DIR:-.}/.claude/logs"
LOG_FILE="${LOG_DIR}/tool-usage.log"

mkdir -p "${LOG_DIR}"

TS=$(date '+%Y-%m-%dT%H:%M:%S%z')
TOOL="${CLAUDE_TOOL:-unknown}"

# Formato: praxis | timestamp | tool=<nombre>
printf 'praxis | %s | tool=%s\n' "${TS}" "${TOOL}" >> "${LOG_FILE}"

# Nunca interrumpe la tool execution.
echo '{}'
