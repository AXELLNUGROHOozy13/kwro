import { Router } from 'express';
import { z } from 'zod';
import { env } from '../../config/env';
import { audit } from '../../lib/audit';
import { activationCodeHint, generateActivationCode, hashActivationCode } from '../../lib/crypto';
import { prisma } from '../../lib/db';
import { ah, notFound } from '../../lib/errors';
import { authenticate } from '../../middleware/auth';

export const developerRouter = Router();
developerRouter.use(authenticate('DEVELOPER'));

const pageSchema = z.object({
  page: z.coerce.number().int().min(1).default(1),
  pageSize: z.coerce.number().int().min(1).max(100).default(20),
});
const idParam = z.object({ id: z.string().uuid() });

function codeStatus(c: { revokedAt: Date | null; expiresAt: Date | null; usedCount: number; maxUses: number }) {
  if (c.revokedAt) return 'REVOKED';
  if (c.expiresAt && c.expiresAt < new Date()) return 'EXPIRED';
  if (c.usedCount >= c.maxUses) return 'EXHAUSTED';
  return 'ACTIVE';
}

developerRouter.get(
  '/stats',
  ah(async (_req, res) => {
    const [admins, activeAdmins, suspendedAdmins, students, exams, codes, recent] = await Promise.all([
      prisma.administrator.count(),
      prisma.administrator.count({ where: { status: 'ACTIVE' } }),
      prisma.administrator.count({ where: { status: 'SUSPENDED' } }),
      prisma.student.count(),
      prisma.exam.count(),
      prisma.activationCode.findMany({ select: { revokedAt: true, expiresAt: true, usedCount: true, maxUses: true } }),
      prisma.adminAuditLog.findMany({
        orderBy: { createdAt: 'desc' },
        take: 10,
        select: { id: true, actorType: true, action: true, targetType: true, createdAt: true },
      }),
    ]);
    const byStatus = { ACTIVE: 0, EXPIRED: 0, EXHAUSTED: 0, REVOKED: 0 };
    for (const c of codes) byStatus[codeStatus(c)]++;
    res.json({
      administrators: { total: admins, active: activeAdmins, suspended: suspendedAdmins },
      students,
      exams,
      activationCodes: { total: codes.length, ...byStatus },
      recentActivity: recent,
      // Database reachability only. Real system health (CPU, queues) arrives with monitoring in Phase 5/6.
      system: { database: 'ok' },
    });
  }),
);

const createCodeSchema = z.object({
  label: z.string().trim().max(100).optional(),
  maxUses: z.number().int().min(1).max(500).default(1),
  expiresInDays: z.number().int().min(1).max(365).optional(),
});

developerRouter.post(
  '/activation-codes',
  ah(async (req, res) => {
    const input = createCodeSchema.parse(req.body);
    const plaintext = generateActivationCode();
    const row = await prisma.activationCode.create({
      data: {
        codeHash: hashActivationCode(plaintext, env.CODE_HASH_PEPPER),
        codeHint: activationCodeHint(plaintext),
        label: input.label || null,
        maxUses: input.maxUses,
        expiresAt: input.expiresInDays ? new Date(Date.now() + input.expiresInDays * 86_400_000) : null,
        createdById: req.auth!.id,
      },
    });
    await audit({
      actorType: 'DEVELOPER',
      actorId: req.auth!.id,
      action: 'activation_code.create',
      targetType: 'ActivationCode',
      targetId: row.id,
      ip: req.ip,
      metadata: { maxUses: row.maxUses, expiresAt: row.expiresAt?.toISOString() ?? null },
    });
    // The only time the plaintext exists. It cannot be recovered later.
    res.status(201).json({ id: row.id, code: plaintext, maxUses: row.maxUses, expiresAt: row.expiresAt });
  }),
);

developerRouter.get(
  '/activation-codes',
  ah(async (req, res) => {
    const { page, pageSize } = pageSchema.parse(req.query);
    const [total, rows] = await Promise.all([
      prisma.activationCode.count(),
      prisma.activationCode.findMany({
        orderBy: { createdAt: 'desc' },
        skip: (page - 1) * pageSize,
        take: pageSize,
        include: { administrators: { select: { id: true, email: true, schoolName: true, createdAt: true } } },
      }),
    ]);
    res.json({
      data: rows.map((c) => ({
        id: c.id,
        hint: c.codeHint,
        label: c.label,
        status: codeStatus(c),
        usedCount: c.usedCount,
        maxUses: c.maxUses,
        expiresAt: c.expiresAt,
        revokedAt: c.revokedAt,
        createdAt: c.createdAt,
        usedBy: c.administrators, // activation history
      })),
      meta: { page, pageSize, total },
    });
  }),
);

developerRouter.post(
  '/activation-codes/:id/revoke',
  ah(async (req, res) => {
    const { id } = idParam.parse(req.params);
    const result = await prisma.activationCode.updateMany({ where: { id, revokedAt: null }, data: { revokedAt: new Date() } });
    if (result.count === 0) throw notFound('Code not found or already revoked.');
    await audit({ actorType: 'DEVELOPER', actorId: req.auth!.id, action: 'activation_code.revoke', targetType: 'ActivationCode', targetId: id, ip: req.ip });
    res.status(204).end();
  }),
);

developerRouter.get(
  '/administrators',
  ah(async (req, res) => {
    const { page, pageSize } = pageSchema.parse(req.query);
    const [total, rows] = await Promise.all([
      prisma.administrator.count(),
      prisma.administrator.findMany({
        orderBy: { createdAt: 'desc' },
        skip: (page - 1) * pageSize,
        take: pageSize,
        select: { id: true, email: true, name: true, schoolName: true, status: true, createdAt: true, _count: { select: { students: true, exams: true } } },
      }),
    ]);
    res.json({ data: rows, meta: { page, pageSize, total } });
  }),
);

async function setAdminStatus(id: string, status: 'ACTIVE' | 'SUSPENDED', developerId: string, ip?: string) {
  const admin = await prisma.administrator.findUnique({ where: { id }, select: { id: true } });
  if (!admin) throw notFound('Administrator not found.');
  await prisma.$transaction([
    prisma.administrator.update({ where: { id }, data: { status } }),
    // Suspending also kills every refresh session, so access ends right away.
    ...(status === 'SUSPENDED'
      ? [prisma.refreshToken.updateMany({ where: { adminId: id, revokedAt: null }, data: { revokedAt: new Date() } })]
      : []),
  ]);
  await audit({
    actorType: 'DEVELOPER',
    actorId: developerId,
    action: status === 'SUSPENDED' ? 'administrator.suspend' : 'administrator.activate',
    targetType: 'Administrator',
    targetId: id,
    ip,
  });
}

developerRouter.post(
  '/administrators/:id/suspend',
  ah(async (req, res) => {
    await setAdminStatus(idParam.parse(req.params).id, 'SUSPENDED', req.auth!.id, req.ip);
    res.status(204).end();
  }),
);
developerRouter.post(
  '/administrators/:id/activate',
  ah(async (req, res) => {
    await setAdminStatus(idParam.parse(req.params).id, 'ACTIVE', req.auth!.id, req.ip);
    res.status(204).end();
  }),
);
