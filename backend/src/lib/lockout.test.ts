import { describe, expect, it, vi } from 'vitest';

vi.mock('./db', () => ({ prisma: {} }));

import { lockDurationMs, lockRemainingMs } from './lockout';

describe('lockout schedule', () => {
  it('does not lock before the 5th failure', () => {
    for (const n of [0, 1, 4]) expect(lockDurationMs('student', n)).toBe(0);
  });
  it('escalates for students: 5 min, 15 min, 60 min', () => {
    expect(lockDurationMs('student', 5)).toBe(5 * 60_000);
    expect(lockDurationMs('student', 10)).toBe(15 * 60_000);
    expect(lockDurationMs('student', 15)).toBe(60 * 60_000);
  });
  it('is stricter for admins and developers', () => {
    expect(lockDurationMs('admin', 5)).toBe(15 * 60_000);
    expect(lockDurationMs('developer', 10)).toBe(60 * 60_000);
    expect(lockDurationMs('admin', 20)).toBe(24 * 60 * 60_000);
  });
  it('reports remaining lock time', () => {
    expect(lockRemainingMs(null)).toBe(0);
    expect(lockRemainingMs(new Date(Date.now() - 1000))).toBe(0);
    expect(lockRemainingMs(new Date(Date.now() + 60_000))).toBeGreaterThan(50_000);
  });
});
