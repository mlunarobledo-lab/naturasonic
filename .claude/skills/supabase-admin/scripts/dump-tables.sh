#!/usr/bin/env bash
# Backup rapido de tablas via pg_dump antes de migracion destructiva.
# Uso: ./scripts/dump-tables.sh [tabla1 tabla2 ...]
# Sin args: dump completo del schema public.

set -euo pipefail

if [ -z "${SUPABASE_DB_URL:-}" ]; then
  echo "Falta SUPABASE_DB_URL en el entorno."
  echo "Conseguir: Supabase Dashboard → Settings → Database → Connection string (URI)."
  exit 1
fi

OUT="backup-$(date +%Y%m%d-%H%M%S).sql"

if [ "$#" -eq 0 ]; then
  echo "Dump completo del schema public → $OUT"
  pg_dump "$SUPABASE_DB_URL" --schema=public --no-owner --no-acl > "$OUT"
else
  echo "Dump de tablas: $@ → $OUT"
  TABLES=""
  for t in "$@"; do
    TABLES="$TABLES --table=public.$t"
  done
  pg_dump "$SUPABASE_DB_URL" $TABLES --no-owner --no-acl > "$OUT"
fi

echo "Listo. Para restaurar: psql \"\$SUPABASE_DB_URL\" < $OUT"
