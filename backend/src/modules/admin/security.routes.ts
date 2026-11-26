import { Router } from 'express';
import rateLimit from 'express-rate-limit';
import { z } from 'zod';
import { audit } from '../../lib/audit';
import { prisma } from '../../lib/db';
import { ah, badRequest, notFound } from '../../lib/errors';
import { clearFailures } from '../../lib/lockout';
import { verifyPassword } from '../../lib/password';
import { consumeRecoveryCode, decryptSecret, encryptSecret, newRecoveryCodes, newTotpSecret, otpauthUri, verifyTotp } from '../../lib/totp';
import { authenticate } from '../../middleware/auth';

/** Mounted at /api/v1/admin/security (before the main admin router). */
export const securityRouter = Router();
securityRouter.use(authenticate('ADMIN'));

const mfaLimiter = rateLimit({
  windowMs: 15 * 60_000, limit: 15, standardHeaders: 'draft-7', legacyHeaders: false,
  message: { error: { code: 'RATE_LIMITED', message: 'Terlalu banyak percobaan. Coba lagi nanti.' } },
});
const me = (req: import('express').Request) => (req.auth as { id: string }).id;
const code6 = z.object({ code: z.string().min(6).max(20) });

securityRouter.get(
  '/',
  ah(async (req, res) => {
    const a = await prisma.administrator.findUniqueOrThrow({ where: { id: me(req) }, select: { totpEnabled: true, recoveryCodeHashes: true } });
    res.json({ mfaEnabled: a.totpEnabled, recoveryCodesLeft: a.recoveryCodeHashes.length });
  }),
);

// Step 1: create a pending secret. It does nothing until /mfa/enable proves the authenticator app has it.
securityRouter.post(
  '/mfa/setup',
  mfaLimiter,
  ah(async (req, res) => {
    const a = await prisma.administrator.findUniqueOrThrow({ where: { id: me(req) } });
    if (a.totpEnabled) throw badRequest('MFA_ALREADY_ON', 'Verifikasi dua langkah sudah aktif.');
    const secret = newTotpSecret();
    await prisma.administrator.update({ where: { id: a.id }, data: { totpSecretEnc: encryptSecret(secret), totpLastStep: null } });
    res.json({ secret, otpauthUri: otpauthUri(a.email, secret) });
  }),
);

// Step 2: confirm with a code. Recovery codes are returned once and only their hashes are stored.
securityRouter.post(
  '/mfa/enable',
  mfaLimiter,
  ah(async (req, res) => {
    const { code } = code6.parse(req.body);
    const a = await prisma.administrator.findUniqueOrThrow({ where: { id: me(req) } });
    if (a.totpEnabled) throw badRequest('MFA_ALREADY_ON', 'Verifikasi dua langkah sudah aktif.');
    if (!a.totpSecretEnc) throw badRequest('MFA_NOT_STARTED', 'Mulai dari langkah pertama dulu.');
    const step = verifyTotp(decryptSecret(a.totpSecretEnc), code.trim(), null);
    if (step === null) throw badRequest('INVALID_MFA_CODE', 'Kode salah. Cek jam HP lalu coba lagi.');
    const rc = newRecoveryCodes();
    await prisma.administrator.update({ where: { id: a.id }, data: { totpEnabled: true, totpLastStep: step, recoveryCodeHashes: rc.hashes } });
    await audit({ actorType: 'ADMIN', actorId: a.id, action: 'admin.mfa.enabled', ip: req.ip });
    res.json({ recoveryCodes: rc.plain });
  }),
);

// Turning it off needs the password AND a current code, so a stolen session alone cannot remove it.
securityRouter.post(
  '/mfa/disable',
  mfaLimiter,
  ah(async (req, res) => {
    const body = z.object({ password: z.string().min(1).max(256), code: z.string().min(6).max(20) }).parse(req.body);
    const a = await prisma.administrator.findUniqueOrThrow({ where: { id: me(req) } });
    if (!a.totpEnabled || !a.totpSecretEnc) throw badRequest('MFA_NOT_ON', 'Verifikasi dua langkah belum aktif.');
    const okPw = await verifyPassword(a.passwordHash, body.password);
    const okCode = verifyTotp(decryptSecret(a.totpSecretEnc), body.code.trim(), a.totpLastStep) !== null || consumeRecoveryCode(a.recoveryCodeHashes, body.code.trim()) !== null;
    if (!okPw || !okCode) throw badRequest('INVALID_CREDENTIALS', 'Kata sandi atau kode salah.');
    await prisma.administrator.update({ where: { id: a.id }, data: { totpEnabled: false, totpSecretEnc: null, totpLastStep: null, recoveryCodeHashes: [] } });
    await audit({ actorType: 'ADMIN', actorId: a.id, action: 'admin.mfa.disabled', ip: req.ip });
    res.status(204).end();
  }),
);

// A supervisor can open a locked student account without waiting for the lock to expire.
securityRouter.post(
  '/students/:id/unlock',
  ah(async (req, res) => {
    const id = z.string().uuid().parse(req.params.id);
    const s = await prisma.student.findFirst({ where: { id, adminId: me(req) }, select: { id: true } });
    if (!s) throw notFound('Siswa tidak ditemukan.');
    await clearFailures('student', id);
    await audit({ actorType: 'ADMIN', actorId: me(req), action: 'student.unlock', targetType: 'Student', targetId: id, ip: req.ip });
    res.status(204).end();
  }),
);
