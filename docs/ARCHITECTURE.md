# KAWREXAM architecture (Phase 1)

## Components
| Component | Tech | Phase |
|---|---|---|
| Backend API | Node.js, Express, Prisma, PostgreSQL | 1 (auth + developer), more later |
| Developer Console | Next.js, TypeScript, Tailwind | 1 |
| Admin Dashboard | Next.js | 2 |
| Student app | Kotlin, Compose, Room | 3-4 |
| Realtime monitoring | Socket.IO on the backend | 5 |

## Decisions
- **Monorepo**, one backend shared by all clients.
- **Hosting:** Railway runs containers, not a root VPS. Nginx, PM2, systemd and ufw do not apply there. The backend ships a `Dockerfile`; HTTPS and the process manager come from the platform. A real VPS script set is only written if the hosting turns out to be a VPS (Phase 6).
- **Student phones are personal, so no Device Owner.** Kiosk = screen pinning + `FLAG_SECURE` + Do Not Disturb + violation detection (Phase 4). This reduces cheating and records it; it cannot guarantee it.
- **Server is authoritative** for time, scoring, session status and violations.

## Auth model
- Access token: JWT (HS256), 15 min, kept in memory by web clients.
- Refresh token: opaque random value, stored hashed, rotated on every use. Reusing an old token revokes the whole family.
- Web clients: refresh token in an `httpOnly`, `SameSite=Strict` cookie; the console proxies `/api` so everything is same-origin. Mobile clients send the refresh token in the request body.
- Every authenticated request re-checks the account in the database, so suspension is immediate.
- Passwords: argon2id. Activation codes: 80-bit random, stored as HMAC-SHA256 with a server pepper, shown once.

## Known gaps in Phase 1
Optional MFA (column reserved), per-account lockout, OpenAPI docs, admin-scoped data isolation (starts with Phase 2 modules), `SystemSetting` uniqueness when `adminId` is null (PostgreSQL allows duplicate NULLs; enforce in code).
