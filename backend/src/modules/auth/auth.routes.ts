import { Router } from 'express';
import rateLimit from 'express-rate-limit';
import { z } from 'zod';
import { env } from '../../config/env';
import { ah, unauthorized } from '../../lib/errors';
import { loginAdmin, loginDeveloper, registerAdmin } from './auth.service';
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
    respond(res, await loginAdmin(email, password, requestMeta(req)));
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
