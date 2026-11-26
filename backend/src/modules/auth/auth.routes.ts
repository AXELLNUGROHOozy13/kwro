import { Router } from 'express';
import rateLimit from 'express-rate-limit';
import { z } from 'zod';
import { env } from '../../config/env';
import { ah, unauthorized } from '../../lib/errors';
import { completeAdminMfa, loginAdmin, loginDeveloper, loginStudent, registerAdmin } from './auth.service';
import {
  REFRESH_COOKIE,
  clearRefreshCookie,
  requestMeta,
  revokeRefreshToken,
  rotateRefreshToken,
  setRefreshCookie,
  type IssuedSession,
} from './tokens';

export const authRouter = Router();

// Brute-force guard for credential endpoints (per IP). Per-account lockout is a later hardening step.
const credentialLimiter = rateLimit({
  windowMs: 15 * 60_000,
  limit: 20,
  standardHeaders: 'draft-7',
  legacyHeaders: false,
  message: { error: { code: 'RATE_LIMITED', message: 'Too many attempts. Try again in a few minutes.' } },
});

const loginSchema = z.object({ email: z.string().email().max(254), password: z.string().min(1).max(256) });
const registerSchema = z.object({
  activationCode: z.string().min(8).max(64),
  email: z.string().email().max(254),
  name: z.string().trim().min(2).max(100),
  schoolName: z.string().trim().min(2).max(150),
  password: z.string().min(12, 'Use at least 12 characters.').max(256),
});

function respond(res: import('express').Response, s: IssuedSession, extra: object = {}) {
  setRefreshCookie(res, s.refreshToken, s.refreshExpiresAt);
  res.json({ accessToken: s.accessToken, expiresIn: env.ACCESS_TOKEN_TTL_SECONDS, ...extra });
}

authRouter.post(
  '/developer/login',
  credentialLimiter,
  ah(async (req, res) => {
    const { email, password } = loginSchema.parse(req.body);
    respond(res, await loginDeveloper(email, password, requestMeta(req)));
  }),
);

authRouter.post(
  '/admin/login',
  credentialLimiter,
  ah(async (req, res) => {
    const { email, password } = loginSchema.parse(req.body);
    const r = await loginAdmin(email, password, requestMeta(req));
    if ('mfaToken' in r) { res.json({ mfaRequired: true, mfaToken: r.mfaToken }); return; }
    respond(res, r.session);
  }),
);

// Second step of an administrator login when two-step verification is on.
authRouter.post(
  '/admin/login/mfa',
  credentialLimiter,
  ah(async (req, res) => {
    const { mfaToken, code } = z.object({ mfaToken: z.string().min(20).max(1000), code: z.string().min(6).max(20) }).parse(req.body);
    respond(res, await completeAdminMfa(mfaToken, code, requestMeta(req)));
  }),
);

authRouter.post(
  '/admin/register',
  credentialLimiter,
  ah(async (req, res) => {
    const { admin, session } = await registerAdmin(registerSchema.parse(req.body), requestMeta(req));
    res.status(201);
    respond(res, session, { admin });
  }),
);

// Accepts the refresh token from the cookie (web) or the body (mobile clients).
authRouter.post(
  '/refresh',
  ah(async (req, res) => {
    const raw = req.cookies?.[REFRESH_COOKIE] ?? (typeof req.body?.refreshToken === 'string' ? req.body.refreshToken : '');
    if (!raw) throw unauthorized('NO_REFRESH_TOKEN', 'Session expired. Please sign in again.');
    try {
      respond(res, await rotateRefreshToken(raw, requestMeta(req)));
    } catch (e) {
      clearRefreshCookie(res);
      throw e;
    }
  }),
);

authRouter.post(
  '/logout',
  ah(async (req, res) => {
    const raw = req.cookies?.[REFRESH_COOKIE] ?? (typeof req.body?.refreshToken === 'string' ? req.body.refreshToken : '');
    if (raw) await revokeRefreshToken(raw);
    clearRefreshCookie(res);
    res.status(204).end();
  }),
);

// ───────── Student app (mobile): tokens travel in the JSON body, not in cookies ─────────
// A whole class often shares one school IP, so limit per login code (failed attempts only) plus a generous per-IP cap.
const studentCodeLimiter = rateLimit({
  windowMs: 15 * 60_000,
  limit: 10,
  skipSuccessfulRequests: true,
  standardHeaders: 'draft-7',
  legacyHeaders: false,
  keyGenerator: (req) => `code:${String(req.body?.loginCode ?? 'none').slice(0, 20)}`,
  message: { error: { code: 'RATE_LIMITED', message: 'Terlalu banyak percobaan. Coba lagi beberapa menit lagi.' } },
});
const studentIpLimiter = rateLimit({
  windowMs: 15 * 60_000,
  limit: 600,
  standardHeaders: 'draft-7',
  legacyHeaders: false,
  message: { error: { code: 'RATE_LIMITED', message: 'Terlalu banyak percobaan. Coba lagi beberapa menit lagi.' } },
});

authRouter.post(
  '/student/login',
  studentIpLimiter,
  studentCodeLimiter,
  ah(async (req, res) => {
    const body = z.object({ loginCode: z.string().regex(/^\d{7}$/, 'Kode login terdiri dari 7 angka.'), password: z.string().min(1).max(256) }).parse(req.body);
    const { session, student } = await loginStudent(body.loginCode, body.password, requestMeta(req));
    res.json({ accessToken: session.accessToken, refreshToken: session.refreshToken, expiresIn: env.ACCESS_TOKEN_TTL_SECONDS, student });
  }),
);

authRouter.post(
  '/student/refresh',
  studentIpLimiter,
  ah(async (req, res) => {
    const raw = z.object({ refreshToken: z.string().min(20).max(200) }).parse(req.body).refreshToken;
    const s = await rotateRefreshToken(raw, requestMeta(req));
    if (s.subject.type !== 'STUDENT') throw unauthorized('INVALID_REFRESH_TOKEN', 'Sesi berakhir. Masuk lagi.');
    res.json({ accessToken: s.accessToken, refreshToken: s.refreshToken, expiresIn: env.ACCESS_TOKEN_TTL_SECONDS });
  }),
);
