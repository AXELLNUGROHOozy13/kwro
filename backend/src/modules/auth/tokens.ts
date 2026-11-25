import { randomUUID } from 'node:crypto';
import type { Request, Response } from 'express';
import { env } from '../../config/env';
import { generateOpaqueToken, sha256 } from '../../lib/crypto';
import { prisma } from '../../lib/db';
import { unauthorized } from '../../lib/errors';
import { signAccessToken, type Role } from '../../lib/jwt';

export interface SessionMeta {
  ip?: string;
  userAgent?: string;
}
export interface Subject {
  type: Role;
  id: string;
}
export interface IssuedSession {
  accessToken: string;
  refreshToken: string;
  refreshExpiresAt: Date;
  refreshRowId: string;
}

export const REFRESH_COOKIE = 'krx_rt';

export const requestMeta = (req: Request): SessionMeta => ({
  ip: req.ip,
  userAgent: req.get('user-agent')?.slice(0, 255),
});

export async function issueSession(subject: Subject, meta: SessionMeta, familyId?: string): Promise<IssuedSession> {
  const refreshToken = generateOpaqueToken();
  const refreshExpiresAt = new Date(Date.now() + env.REFRESH_TOKEN_TTL_DAYS * 86_400_000);
  const row = await prisma.refreshToken.create({
    data: {
      tokenHash: sha256(refreshToken),
      familyId: familyId ?? randomUUID(),
      subjectType: subject.type,
      developerId: subject.type === 'DEVELOPER' ? subject.id : undefined,
      adminId: subject.type === 'ADMIN' ? subject.id : undefined,
      studentId: subject.type === 'STUDENT' ? subject.id : undefined,
      expiresAt: refreshExpiresAt,
      ip: meta.ip,
      userAgent: meta.userAgent,
    },
  });
  return {
    accessToken: signAccessToken({ sub: subject.id, role: subject.type }),
    refreshToken,
    refreshExpiresAt,
    refreshRowId: row.id,
  };
}

async function assertSubjectActive(subject: Subject): Promise<void> {
  let active = false;
  if (subject.type === 'DEVELOPER') {
    active = !!(await prisma.developer.findUnique({ where: { id: subject.id } }))?.isActive;
  } else if (subject.type === 'ADMIN') {
    active = (await prisma.administrator.findUnique({ where: { id: subject.id } }))?.status === 'ACTIVE';
  } else {
    active = (await prisma.student.findUnique({ where: { id: subject.id } }))?.status === 'ACTIVE';
  }
  if (!active) throw unauthorized('ACCOUNT_INACTIVE', 'Account is not active.');
}

/**
 * Refresh-token rotation with reuse detection:
 * each token works once; presenting an already-used token revokes the whole family.
 */
export async function rotateRefreshToken(raw: string, meta: SessionMeta): Promise<IssuedSession & { subject: Subject }> {
  const row = await prisma.refreshToken.findUnique({ where: { tokenHash: sha256(raw) } });
  if (!row) throw unauthorized('INVALID_REFRESH_TOKEN', 'Session expired. Please sign in again.');

  // Atomic claim: only one concurrent request can flip revokedAt from null.
  const claimed = await prisma.refreshToken.updateMany({
    where: { id: row.id, revokedAt: null },
    data: { revokedAt: new Date() },
  });
  if (claimed.count !== 1) {
    await prisma.refreshToken.updateMany({
      where: { familyId: row.familyId, revokedAt: null },
      data: { revokedAt: new Date() },
    });
    throw unauthorized('REFRESH_TOKEN_REUSED', 'Session expired. Please sign in again.');
  }
  if (row.expiresAt < new Date()) throw unauthorized('INVALID_REFRESH_TOKEN', 'Session expired. Please sign in again.');

  const subject: Subject = {
    type: row.subjectType,
    id: (row.developerId ?? row.adminId ?? row.studentId) as string,
  };
  await assertSubjectActive(subject);
  const next = await issueSession(subject, meta, row.familyId);
  await prisma.refreshToken.update({ where: { id: row.id }, data: { replacedById: next.refreshRowId } });
  return { ...next, subject };
}

export async function revokeRefreshToken(raw: string): Promise<void> {
  const row = await prisma.refreshToken.findUnique({ where: { tokenHash: sha256(raw) } });
  if (!row) return;
  await prisma.refreshToken.updateMany({ where: { familyId: row.familyId, revokedAt: null }, data: { revokedAt: new Date() } });
}

// Web clients keep the refresh token in an httpOnly cookie (invisible to scripts).
export function setRefreshCookie(res: Response, token: string, expires: Date): void {
  res.cookie(REFRESH_COOKIE, token, {
    httpOnly: true,
    secure: env.COOKIE_SECURE,
    sameSite: 'strict',
    path: '/api/v1/auth',
    expires,
  });
}
export const clearRefreshCookie = (res: Response): void => {
  res.clearCookie(REFRESH_COOKIE, { path: '/api/v1/auth' });
};
