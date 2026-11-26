import { createCipheriv, createDecipheriv, createHmac, hkdfSync, randomBytes, randomInt } from 'node:crypto';
import jwt from 'jsonwebtoken';
import { env } from '../config/env';
import { unauthorized } from './errors';

const B32 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

export function base32Encode(buf: Buffer): string {
  let bits = 0, value = 0, out = '';
  for (const byte of buf) {
    value = (value << 8) | byte; bits += 8;
    while (bits >= 5) { out += B32[(value >>> (bits - 5)) & 31]; bits -= 5; }
  }
  if (bits > 0) out += B32[(value << (5 - bits)) & 31];
  return out;
}

export function base32Decode(s: string): Buffer {
  let bits = 0, value = 0;
  const out: number[] = [];
  for (const c of s.toUpperCase().replace(/[^A-Z2-7]/g, '')) {
    value = (value << 5) | B32.indexOf(c); bits += 5;
    if (bits >= 8) { out.push((value >>> (bits - 8)) & 255); bits -= 8; }
  }
  return Buffer.from(out);
}

export const newTotpSecret = (): string => base32Encode(randomBytes(20));

/** RFC 4226 HOTP (HMAC-SHA1, 6 digits). */
function hotp(key: Buffer, counter: number): string {
  const msg = Buffer.alloc(8);
  msg.writeBigUInt64BE(BigInt(counter));
  const h = createHmac('sha1', key).update(msg).digest();
  const o = h[h.length - 1] & 15;
  const n = ((h[o] & 0x7f) << 24) | (h[o + 1] << 16) | (h[o + 2] << 8) | h[o + 3];
  return String(n % 1_000_000).padStart(6, '0');
}

const STEP_S = 30;
const safeEq = (a: string, b: string) => a.length === b.length && a.split('').reduce((acc, ch, i) => acc | (ch.charCodeAt(0) ^ b.charCodeAt(i)), 0) === 0;

/** Returns the matched 30-second step, or null. Accepts ±1 step of clock drift; a step at or before `lastStep` is rejected (replay). */
export function verifyTotp(secretB32: string, code: string, lastStep?: number | null): number | null {
  if (!/^\d{6}$/.test(code)) return null;
  const key = base32Decode(secretB32);
  const now = Math.floor(Date.now() / 1000 / STEP_S);
  let hit: number | null = null;
  for (const step of [now - 1, now, now + 1]) {
    if (safeEq(hotp(key, step), code) && (lastStep == null || step > lastStep)) hit = step; // no early exit: constant work
  }
  return hit;
}

export const otpauthUri = (email: string, secret: string) =>
  `otpauth://totp/KAWREXAM:${encodeURIComponent(email)}?secret=${secret}&issuer=KAWREXAM&algorithm=SHA1&digits=6&period=30`;

// ───────── Secret at rest: AES-256-GCM, key derived from CODE_HASH_PEPPER (do not rotate it; see README) ─────────
const key = () => Buffer.from(hkdfSync('sha256', env.CODE_HASH_PEPPER, 'kawrexam', 'totp-secret-v1', 32));

export function encryptSecret(plain: string): string {
  const iv = randomBytes(12);
  const c = createCipheriv('aes-256-gcm', key(), iv);
  const enc = Buffer.concat([c.update(plain, 'utf8'), c.final()]);
  return [iv, c.getAuthTag(), enc].map((b) => b.toString('base64')).join('.');
}

export function decryptSecret(blob: string): string {
  const [iv, tag, enc] = blob.split('.').map((p) => Buffer.from(p, 'base64'));
  const d = createDecipheriv('aes-256-gcm', key(), iv);
  d.setAuthTag(tag);
  return Buffer.concat([d.update(enc), d.final()]).toString('utf8');
}

// ───────── Recovery codes (single use) ─────────
const hashRecovery = (code: string) => createHmac('sha256', env.CODE_HASH_PEPPER).update(code.toUpperCase().replace(/[^A-Z0-9]/g, '')).digest('hex');

export function newRecoveryCodes(n = 8): { plain: string[]; hashes: string[] } {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  const plain = Array.from({ length: n }, () => {
    const pick = (k: number) => Array.from({ length: k }, () => alphabet[randomInt(alphabet.length)]).join('');
    return `${pick(5)}-${pick(5)}`;
  });
  return { plain, hashes: plain.map(hashRecovery) };
}

/** Returns the remaining hashes if `code` was a valid recovery code (it is consumed), otherwise null. */
export function consumeRecoveryCode(hashes: string[], code: string): string[] | null {
  const h = hashRecovery(code);
  return hashes.includes(h) ? hashes.filter((x) => x !== h) : null;
}

// ───────── Short-lived token between "password OK" and "code OK" ─────────
// No `role` claim and a different issuer, so verifyAccessToken() rejects it: it can never open an API.
export const signMfaToken = (adminId: string) => jwt.sign({ purpose: 'mfa' }, env.JWT_ACCESS_SECRET, { subject: adminId, issuer: 'kawrexam-mfa', expiresIn: 300 });

export function verifyMfaToken(token: string): string {
  try {
    const p = jwt.verify(token, env.JWT_ACCESS_SECRET, { issuer: 'kawrexam-mfa', algorithms: ['HS256'] });
    if (typeof p === 'string' || p.purpose !== 'mfa' || !p.sub) throw new Error('bad');
    return p.sub;
  } catch {
    throw unauthorized('MFA_EXPIRED', 'Verifikasi kedaluwarsa. Masuk lagi dengan email dan kata sandi.');
  }
}
