import type { Prisma } from '@prisma/client';
import { prisma } from './db';

interface AuditInput {
  actorType: 'DEVELOPER' | 'ADMIN' | 'STUDENT';
  actorId?: string;
  action: string;
  targetType?: string;
  targetId?: string;
  metadata?: Prisma.InputJsonValue;
  ip?: string;
}

/** Never put secrets (codes, passwords, tokens) into metadata. */
export async function audit(input: AuditInput): Promise<void> {
  await prisma.adminAuditLog.create({
    data: {
      actorType: input.actorType,
      developerId: input.actorType === 'DEVELOPER' ? input.actorId : undefined,
      adminId: input.actorType === 'ADMIN' ? input.actorId : undefined,
      action: input.action,
      targetType: input.targetType,
      targetId: input.targetId,
      metadata: input.metadata,
      ip: input.ip,
    },
  });
}
