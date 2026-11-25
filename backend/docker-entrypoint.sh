#!/bin/sh
set -e

# 1) Database schema. Real migrations when they exist; `db push` only for the very first deploy.
if [ -d prisma/migrations ] && [ -n "$(ls -A prisma/migrations 2>/dev/null)" ]; then
  npx prisma migrate deploy
else
  echo "[entrypoint] No migrations found: using prisma db push (first deploy only)."
  npx prisma db push --skip-generate
fi

# 2) Optional first developer account. Skipped when the variables are unset or the account already exists.
if [ -n "$DEV_EMAIL" ] && [ -n "$DEV_NAME" ] && [ -n "$DEV_PASSWORD" ]; then
  npx tsx scripts/create-developer.ts
fi

exec node dist/server.js
