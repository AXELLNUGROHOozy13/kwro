import jwt from 'jsonwebtoken';
import { env } from '../config/env';
import { unauthorized } from './errors';

export type Role = 'DEVELOPER' | 'ADMIN' | 'STUDENT';
export interface AccessClaims {
  sub: string;
  role: Role;
}

const ISSUER = 'kawrexam';

export function signAccessToken({ sub, role }: AccessClaims): string {
  return jwt.sign({ role }, env.JWT_ACCESS_SECRET, {
    subject: sub,
    issuer: ISSUER,
    algorithm: 'HS256',
    expiresIn: env.ACCESS_TOKEN_TTL_SECONDS,
  });
}

export function verifyAccessToken(token: string): AccessClaims {
  try {
    const payload = jwt.verify(token, env.JWT_ACCESS_SECRET, {
      algorithms: ['HS256'],
      issuer: ISSUER,
    });
    if (typeof payload === 'string' || !payload.sub || !payload.role) throw new Error('bad claims');
    return { sub: payload.sub, role: payload.role as Role };
  } catch {
    throw unauthorized('INVALID_TOKEN', 'Access token is invalid or expired.');
  }
}
