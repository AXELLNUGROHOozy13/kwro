import { describe, expect, it } from 'vitest';
import {
  activationCodeHint,
  generateActivationCode,
  generateOpaqueToken,
  hashActivationCode,
  normalizeActivationCode,
} from './crypto';

const PEPPER = 'p'.repeat(32);

describe('activation codes', () => {
  it('has the expected format and alphabet', () => {
    const code = generateActivationCode();
    expect(code).toMatch(/^KRX(-[A-HJ-NP-Z2-9]{4}){4}$/);
  });

  it('does not repeat across 2000 generations', () => {
    const set = new Set(Array.from({ length: 2000 }, generateActivationCode));
    expect(set.size).toBe(2000);
  });

  it('normalizes case, dashes and spaces to the same hash', () => {
    const a = hashActivationCode('KRX-ABCD-EFGH-JKLM-NPQR', PEPPER);
    const b = hashActivationCode('krx abcd efgh jklm npqr', PEPPER);
    expect(a).toBe(b);
    expect(normalizeActivationCode('krx-ab cd')).toBe('KRXABCD');
  });

  it('changes the hash when the pepper changes and never contains the code', () => {
    const code = 'KRX-ABCD-EFGH-JKLM-NPQR';
    const h1 = hashActivationCode(code, PEPPER);
    const h2 = hashActivationCode(code, 'q'.repeat(32));
    expect(h1).not.toBe(h2);
    expect(h1).toMatch(/^[0-9a-f]{64}$/);
    expect(h1).not.toContain('ABCD');
  });

  it('hint is the last 4 characters', () => {
    expect(activationCodeHint('KRX-ABCD-EFGH-JKLM-NPQR')).toBe('NPQR');
  });
});

describe('opaque tokens', () => {
  it('are unique and long enough', () => {
    const a = generateOpaqueToken();
    const b = generateOpaqueToken();
    expect(a).not.toBe(b);
    expect(a.length).toBeGreaterThanOrEqual(64);
  });
});
