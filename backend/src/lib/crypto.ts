import { createHash, createHmac, randomBytes, randomInt } from 'node:crypto';

// 32 symbols, no 0/O/1/I, so codes are easy to read aloud and type.
const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

/** KRX-XXXX-XXXX-XXXX-XXXX: 16 random symbols = 80 bits of entropy. */
export function generateActivationCode(): string {
  const groups = Array.from({ length: 4 }, () =>
    Array.from({ length: 4 }, () => ALPHABET[randomInt(ALPHABET.length)]).join(''),
  );
  return `KRX-${groups.join('-')}`;
}

/** Strips dashes/spaces and uppercases so "krx 1234..." and "KRX-1234..." hash the same. */
export function normalizeActivationCode(input: string): string {
  return input.toUpperCase().replace(/[^A-Z0-9]/g, '');
}

/** Keyed hash. The code is high-entropy, so a fast HMAC is appropriate (no need for argon2). */
export function hashActivationCode(code: string, pepper: string): string {
  return createHmac('sha256', pepper).update(normalizeActivationCode(code)).digest('hex');
}

export function activationCodeHint(code: string): string {
  return normalizeActivationCode(code).slice(-4);
}

export function generateOpaqueToken(bytes = 48): string {
  return randomBytes(bytes).toString('base64url');
}

export function sha256(value: string): string {
  return createHash('sha256').update(value).digest('hex');
}
