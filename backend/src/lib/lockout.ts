import { prisma } from './db';
import { AppError } from './errors';

export type LockKind = 'student' | 'admin' | 'developer';

interface Delegate {
  update(args: { where: { id: string }; data: Record<string, unknown> }): Promise<{ failedLogins: number; lockedUntil: Date | null }>;
}
const delegate = (k: LockKind): Delegate =>
  (k === 'student' ? prisma.student : k === 'admin' ? prisma.administrator : prisma.developer) as unknown as Delegate;

const MIN = 60_000;
/** Lock length after the Nth consecutive failure. Students get shorter locks (a supervisor is in the room and can unlock). */
export function lockDurationMs(kind: LockKind, failures: number): number {
  if (failures < 5) return 0;
  const tier = failures >= 15 ? 2 : failures >= 10 ? 1 : 0;
  return (kind === 'student' ? [5 * MIN, 15 * MIN, 60 * MIN] : [15 * MIN, 60 * MIN, 24 * 60 * MIN])[tier];
}

export const lockRemainingMs = (lockedUntil: Date | null | undefined): number =>
  lockedUntil ? Math.max(0, lockedUntil.getTime() - Date.now()) : 0;

export const lockedError = (kind: LockKind, ms: number) =>
  kind === 'student'
    ? new AppError(423, 'ACCOUNT_LOCKED', `Akun terkunci sementara karena salah kata sandi berulang. Coba lagi dalam ${Math.ceil(ms / MIN)} menit, atau minta pengawas membuka kunci.`)
    // Admins/developers get a generic message so the lock state does not reveal that an email exists.
    : new AppError(429, 'RATE_LIMITED', 'Terlalu banyak percobaan. Coba lagi nanti.');

/**
 * Records a failed login. Failures older than 30 minutes (and not locked) are forgotten first, so an occasional typo
 * never adds up over weeks. Returns the lock that this failure caused, or 0.
 */
export async function registerFailure(kind: LockKind, id: string, current: { failedLogins: number; lastFailedAt: Date | null; lockedUntil: Date | null }): Promise<number> {
  const stale = !current.lastFailedAt || Date.now() - current.lastFailedAt.getTime() > 30 * MIN;
  const base = stale && lockRemainingMs(current.lockedUntil) === 0 ? 0 : current.failedLogins;
  const failures = base + 1;
  const ms = lockDurationMs(kind, failures);
  await delegate(kind).update({
    where: { id },
    data: { failedLogins: failures, lastFailedAt: new Date(), lockedUntil: ms > 0 ? new Date(Date.now() + ms) : null },
  });
  return ms;
}

export async function clearFailures(kind: LockKind, id: string): Promise<void> {
  await delegate(kind).update({ where: { id }, data: { failedLogins: 0, lastFailedAt: null, lockedUntil: null } });
}
