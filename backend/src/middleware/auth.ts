import type { NextFunction, Request, Response } from 'express';
import { ah, forbidden, unauthorized } from '../lib/errors';
import { prisma } from '../lib/db';
import { verifyAccessToken, type Role } from '../lib/jwt';

export interface AuthContext {
  id: string;
  role: Role;
}

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Express {
    interface Request {
      auth?: AuthContext;
    }
  }
}

/**
 * Verifies the bearer token, then re-checks the account in the database on every request,
 * so suspending an administrator or developer takes effect immediately (not after token expiry).
 */
export const authenticate = (...allowed: Role[]) =>
  ah(async (req: Request, _res: Response, next: NextFunction) => {
    const header = req.headers.authorization;
    if (!header?.startsWith('Bearer ')) throw unauthorized();
    const claims = verifyAccessToken(header.slice(7));
    if (allowed.length > 0 && !allowed.includes(claims.role)) throw forbidden();

    if (claims.role === 'DEVELOPER') {
      const dev = await prisma.developer.findUnique({ where: { id: claims.sub }, select: { isActive: true } });
      if (!dev?.isActive) throw unauthorized('ACCOUNT_INACTIVE', 'Account is inactive.');
    } else if (claims.role === 'ADMIN') {
      const admin = await prisma.administrator.findUnique({ where: { id: claims.sub }, select: { status: true } });
      if (admin?.status !== 'ACTIVE') throw unauthorized('ACCOUNT_SUSPENDED', 'Account is suspended.');
    } else {
      const student = await prisma.student.findUnique({ where: { id: claims.sub }, select: { status: true } });
      if (student?.status !== 'ACTIVE') throw unauthorized('ACCOUNT_DISABLED', 'Account is disabled.');
    }

    req.auth = { id: claims.sub, role: claims.role };
    next();
  });
