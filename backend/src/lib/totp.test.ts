import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('../config/env', () => ({ env: { CODE_HASH_PEPPER: 'p'.repeat(40), JWT_ACCESS_SECRET: 's'.repeat(40) } }));

import { base32Decode, base32Encode, consumeRecoveryCode, decryptSecret, encryptSecret, newRecoveryCodes, signMfaToken, verifyMfaToken, verifyTotp } from './totp';
import { verifyAccessToken } from './jwt';

// RFC 6238 appendix B: SHA-1 secret "12345678901234567890"; at T=59 s the 6-digit code is 287082.
const RFC_SECRET = base32Encode(Buffer.from('12345678901234567890'));

describe('totp', () => {
  afterEach(() => vi.useRealTimers());

  it('accepts the RFC 6238 test vector', () => {
    vi.useFakeTimers(); vi.setSystemTime(59_000);
    expect(verifyTotp(RFC_SECRET, '287082', null)).not.toBeNull();
  });

  it('rejects wrong codes and malformed input', () => {
    vi.useFakeTimers(); vi.setSystemTime(59_000);
    expect(verifyTotp(RFC_SECRET, '000000', null)).toBeNull();
    expect(verifyTotp(RFC_SECRET, '28708', null)).toBeNull();
    expect(verifyTotp(RFC_SECRET, 'abcdef', null)).toBeNull();
  });

  it('refuses a code from a step that was already used (replay)', () => {
    vi.useFakeTimers(); vi.setSystemTime(59_000);
    const step = verifyTotp(RFC_SECRET, '287082', null)!;
    expect(verifyTotp(RFC_SECRET, '287082', step)).toBeNull();
  });

  it('round-trips base32 and encrypted secrets', () => {
    expect(base32Decode(base32Encode(Buffer.from('hello'))).toString()).toBe('hello');
    expect(decryptSecret(encryptSecret(RFC_SECRET))).toBe(RFC_SECRET);
    expect(encryptSecret(RFC_SECRET)).not.toBe(encryptSecret(RFC_SECRET)); // fresh IV every time
  });

  it('recovery codes work once, ignoring case and dashes', () => {
    const rc = newRecoveryCodes(3);
    const left = consumeRecoveryCode(rc.hashes, rc.plain[1].toLowerCase().replace('-', ''));
    expect(left).toHaveLength(2);
    expect(consumeRecoveryCode(left!, rc.plain[1])).toBeNull();
  });

  it('the MFA token can never be used as an access token', () => {
    const t = signMfaToken('11111111-1111-1111-1111-111111111111');
    expect(verifyMfaToken(t)).toBe('11111111-1111-1111-1111-111111111111');
    expect(() => verifyAccessToken(t)).toThrow();
  });
});
