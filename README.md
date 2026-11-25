# KAWREXAM

Secure examination ecosystem: backend API, Developer Console, Admin Dashboard, student Android app.

**Status:** Phase 1 (foundation) written, not yet run. See `docs/ARCHITECTURE.md`.

## Quick start (local)

Requirements: Node 20+, PostgreSQL 14+.

```bash
cp .env.example .env            # fill JWT_ACCESS_SECRET and CODE_HASH_PEPPER (openssl rand -base64 48)
cd backend
npm install
npx prisma migrate dev --name init   # creates the database tables and the first migration
DEV_EMAIL=you@example.com DEV_NAME="Your Name" DEV_PASSWORD='at-least-12-chars' npm run create-developer
npm run dev                          # API on :3000, health at /health

cd ../developer-console
npm install
npm run dev                          # console on :3001, proxies /api to BACKEND_URL
```

Commit `backend/prisma/migrations/` and the `package-lock.json` files after the first run.

## Layout
`backend/` API + Prisma schema, `developer-console/` Next.js console, `admin-dashboard/` (Phase 2), `android-app/` (Phase 3), `docs/`, `.github/workflows/`.

## Docker

```bash
cp .env.example .env     # fill POSTGRES_PASSWORD, JWT_ACCESS_SECRET, CODE_HASH_PEPPER
                         # optional: DEV_EMAIL, DEV_NAME, DEV_PASSWORD creates the first developer on boot
docker compose up --build
```
Console: http://localhost:3001 - API health: http://localhost:3000/health.
Remove `DEV_PASSWORD` from `.env` (or from the Railway variables) after the first successful boot.

On Railway, create two services from this repo: root directory `backend` and root directory `developer-console` (set `BACKEND_URL` on the console service; it is passed to the build as an argument).

## Deploy with pm2 (VPS)

```bash
# backend
cd backend
cp .env.example .env && nano .env        # DATABASE_URL, JWT_ACCESS_SECRET, CODE_HASH_PEPPER
npm install
npx prisma db push                       # first deploy only, until real migrations exist
DEV_EMAIL=you@example.com DEV_NAME="Your Name" DEV_PASSWORD='at-least-12-chars' npm run create-developer
npm run build

# developer console (developer-console/.env already points BACKEND_URL at 127.0.0.1:3000)
cd ../developer-console
npm install && npm run build

# start both
cd .. && pm2 start ecosystem.config.cjs && pm2 save
```
Check the API with `curl http://127.0.0.1:3000/health`; the console listens on port 3001. Put Nginx/Caddy (or your platform's HTTPS) in front, then set `COOKIE_SECURE=true` in `backend/.env` and `pm2 restart kawrexam-api`.
`.env` files are ignored by git: create them on the server.
