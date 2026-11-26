import { env } from '../../config/env';
import { activationCodeHint, hashActivationCode } from '../../lib/crypto';
import { prisma } from '../../lib/db';
import { badRequest, unauthorized } from '../../lib/errors';
import { burnPasswordCheck, hashPassword, verifyPassword } from '../../lib/password';
import { audit } from '../../lib/audit';
import { clearFailures, lockRemainingMs, lockedError, registerFailure } from '../../lib/lockout';
import { consumeRecoveryCode, decryptSecret, signMfaToken, verifyMfaToken, verifyTotp } from '../../lib/totp';
import { AppError } from '../../lib/errors';
import { issueSession, type SessionMeta } from './tokens';

const INVALID_LOGIN = () => unauthorized('INVALID_CREDENTIALS', 'Email or password is incorrect.');

export async function loginDeveloper(email: string, password: string, meta: SessionMeta) {
  const dev = await prisma.developer.findUnique({ where: { email: email.toLowerCase() } });
  if (!dev) {
    await burnPasswordCheck(password);
    throw INVALID_LOGIN();
  }
  const left = lockRemainingMs(dev.lockedUntil);
  if (left > 0) {
    await burnPasswordCheck(password);
    await audit({ actorType: 'DEVELOPER', actorId: dev.id, action: 'developer.login.blocked_locked', ip: meta.ip });
    throw lockedError('developer', left);
  }
  if (!(await verifyPassword(dev.passwordHash, password)) || !dev.isActive) {
    await registerFailure('developer', dev.id, dev);
    throw INVALID_LOGIN();
  }
  await clearFailures('developer', dev.id);
  await audit({ actorType: 'DEVELOPER', actorId: dev.id, action: 'developer.login', ip: meta.ip });
  return issueSession({ type: 'DEVELOPER', id: dev.id }, meta);
}

/** Step 1. Returns a session, or an MFA challenge when the administrator has two-step verification on. */
export async function loginAdmin(email: string, password: string, meta: SessionMeta) {
  const admin = await prisma.administrator.findUnique({ where: { email: email.toLowerCase() } });
  if (!admin) {
    await burnPasswordCheck(password);
    throw INVALID_LOGIN();
  }
  const left = lockRemainingMs(admin.lockedUntil);
  if (left > 0) {
    await burnPasswordCheck(password);
    await audit({ actorType: 'ADMIN', actorId: admin.id, action: 'admin.login.blocked_locked', ip: meta.ip });
    throw lockedError('admin', left);
  }
  if (!(await verifyPassword(admin.passwordHash, password))) {
    await registerFailure('admin', admin.id, admin);
    await audit({ actorType: 'ADMIN', actorId: admin.id, action: 'admin.login.failed', ip: meta.ip });
    throw INVALID_LOGIN();
  }
  if (admin.status !== 'ACTIVE') throw unauthorized('ACCOUNT_SUSPENDED', 'This account is suspended. Contact the developer.');
  if (admin.totpEnabled) return { mfaToken: signMfaToken(admin.id) }; // failures are cleared only after the second step
  await clearFailures('admin', admin.id);
  await audit({ actorType: 'ADMIN', actorId: admin.id, action: 'admin.login', ip: meta.ip });
  return { session: await issueSession({ type: 'ADMIN', id: admin.id }, meta) };
}

/** Step 2. Accepts an authenticator code or a one-time recovery code. Wrong codes count toward the same lockout. */
export async function completeAdminMfa(mfaToken: string, code: string, meta: SessionMeta) {
  const id = verifyMfaToken(mfaToken);
  const admin = await prisma.administrator.findUnique({ where: { id } });
  if (!admin || !admin.totpEnabled || !admin.totpSecretEnc) throw unauthorized('MFA_EXPIRED', 'Verifikasi kedaluwarsa. Masuk lagi.');
  const left = lockRemainingMs(admin.lockedUntil);
  if (left > 0) throw lockedError('admin', left);
  if (admin.status !== 'ACTIVE') throw unauthorized('ACCOUNT_SUSPENDED', 'This account is suspended. Contact the developer.');

  const clean = code.trim();
  let ok = false;
  const step = verifyTotp(decryptSecret(admin.totpSecretEnc), clean, admin.totpLastStep);
  if (step !== null) {
    ok = true;
    await prisma.administrator.update({ where: { id }, data: { totpLastStep: step } });
  } else {
    const remaining = consumeRecoveryCode(admin.recoveryCodeHashes, clean);
    if (remaining) {
      ok = true;
      await prisma.administrator.update({ where: { id }, data: { recoveryCodeHashes: remaining } });
      await audit({ actorType: 'ADMIN', actorId: id, action: 'admin.mfa.recovery_code_used', ip: meta.ip });
    }
  }
  if (!ok) {
    const lock = await registerFailure('admin', id, admin);
    await audit({ actorType: 'ADMIN', actorId: id, action: 'admin.mfa.failed', ip: meta.ip });
    if (lock > 0) throw lockedError('admin', lock);
    throw new AppError(401, 'INVALID_MFA_CODE', 'Kode verifikasi salah.');
  }
  await clearFailures('admin', id);
  await audit({ actorType: 'ADMIN', actorId: id, action: 'admin.login', ip: meta.ip, metadata: { mfa: true } });
  return issueSession({ type: 'ADMIN', id }, meta);
}

export interface RegisterAdminInput {
  activationCode: string;
  email: string;
  name: string;
  schoolName: string;
  password: string;
}

/**
 * Consumes one use of an activation code and creates the administrator in a single transaction.
 * If account creation fails (for example the email is taken), the use is rolled back.
 */
export async function registerAdmin(input: RegisterAdminInput, meta: SessionMeta) {
  const codeHash = hashActivationCode(input.activationCode, env.CODE_HASH_PEPPER);
  const passwordHash = await hashPassword(input.password); // slow work stays outside the transaction
  const invalid = () => badRequest('INVALID_ACTIVATION_CODE', 'Activation code is invalid, expired, or already used.');

  const admin = await prisma.$transaction(async (tx) => {
    const code = await tx.activationCode.findUnique({ where: { codeHash } });
    if (!code || code.revokedAt || (code.expiresAt && code.expiresAt < new Date())) throw invalid();

    // Conditional update makes the usage limit race-safe: two simultaneous requests cannot both win the last use.
    const claimed = await tx.activationCode.updateMany({
      where: { id: code.id, revokedAt: null, usedCount: { lt: code.maxUses } },
      data: { usedCount: { increment: 1 } },
    });
    if (claimed.count !== 1) throw invalid();

    return tx.administrator.create({
      data: {
        email: input.email.toLowerCase(),
        name: input.name,
        schoolName: input.schoolName,
        passwordHash,
        activationCodeId: code.id,
      },
      select: { id: true, email: true, name: true, schoolName: true },
    });
  });

  await audit({
    actorType: 'ADMIN',
    actorId: admin.id,
    action: 'admin.register',
    ip: meta.ip,
    metadata: { codeHint: activationCodeHint(input.activationCode) },
  });
  const session = await issueSession({ type: 'ADMIN', id: admin.id }, meta);
  return { admin, session };
}

export async function loginStudent(loginCode: string, password: string, meta: SessionMeta) {
  const invalid = () => unauthorized('INVALID_CREDENTIALS', 'Kode login atau kata sandi salah.');
  const st = await prisma.student.findUnique({ where: { loginCode }, include: { class: { select: { name: true } } } });
  if (!st) {
    await burnPasswordCheck(password);
    throw invalid();
  }
  const left = lockRemainingMs(st.lockedUntil);
  if (left > 0) {
    await burnPasswordCheck(password); // a locked account never gets a free password check
    throw lockedError('student', left);
  }
  if (!(await verifyPassword(st.passwordHash, password))) {
    const lock = await registerFailure('student', st.id, st);
    await audit({ actorType: 'STUDENT', actorId: st.id, action: 'student.login.failed', ip: meta.ip });
    if (lock > 0) throw lockedError('student', lock);
    throw invalid();
  }
  if (st.status !== 'ACTIVE') throw unauthorized('ACCOUNT_DISABLED', 'Akun dinonaktifkan. Hubungi pengawas.');
  await clearFailures('student', st.id);
  const session = await issueSession({ type: 'STUDENT', id: st.id }, meta);
  return {
    session,
    student: { id: st.id, name: st.name, className: st.class?.name ?? null, mustChangePassword: st.mustChangePassword },
  };
}
