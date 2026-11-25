import { env } from '../../config/env';
import { activationCodeHint, hashActivationCode } from '../../lib/crypto';
import { prisma } from '../../lib/db';
import { badRequest, unauthorized } from '../../lib/errors';
import { burnPasswordCheck, hashPassword, verifyPassword } from '../../lib/password';
import { audit } from '../../lib/audit';
import { issueSession, type SessionMeta } from './tokens';

const INVALID_LOGIN = () => unauthorized('INVALID_CREDENTIALS', 'Email or password is incorrect.');

export async function loginDeveloper(email: string, password: string, meta: SessionMeta) {
  const dev = await prisma.developer.findUnique({ where: { email: email.toLowerCase() } });
  if (!dev) {
    await burnPasswordCheck(password);
    throw INVALID_LOGIN();
  }
  if (!(await verifyPassword(dev.passwordHash, password)) || !dev.isActive) throw INVALID_LOGIN();
  await audit({ actorType: 'DEVELOPER', actorId: dev.id, action: 'developer.login', ip: meta.ip });
  return issueSession({ type: 'DEVELOPER', id: dev.id }, meta);
}

export async function loginAdmin(email: string, password: string, meta: SessionMeta) {
  const admin = await prisma.administrator.findUnique({ where: { email: email.toLowerCase() } });
  if (!admin) {
    await burnPasswordCheck(password);
    throw INVALID_LOGIN();
  }
  if (!(await verifyPassword(admin.passwordHash, password))) throw INVALID_LOGIN();
  if (admin.status !== 'ACTIVE') throw unauthorized('ACCOUNT_SUSPENDED', 'This account is suspended. Contact the developer.');
  await audit({ actorType: 'ADMIN', actorId: admin.id, action: 'admin.login', ip: meta.ip });
  return issueSession({ type: 'ADMIN', id: admin.id }, meta);
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
