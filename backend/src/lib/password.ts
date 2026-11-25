import argon2 from 'argon2';

// OWASP-recommended argon2id baseline (19 MiB, 2 iterations, 1 lane).
const OPTIONS = { type: argon2.argon2id, memoryCost: 19456, timeCost: 2, parallelism: 1 } as const;

export function hashPassword(password: string): Promise<string> {
  return argon2.hash(password, OPTIONS);
}

export async function verifyPassword(hash: string, password: string): Promise<boolean> {
  try {
    return await argon2.verify(hash, password);
  } catch {
    return false;
  }
}

let dummyHash: Promise<string> | undefined;
/** Verifies against a throwaway hash so unknown accounts cost the same time as known ones. */
export async function burnPasswordCheck(password: string): Promise<void> {
  dummyHash ??= hashPassword('kawrexam-dummy-password');
  await verifyPassword(await dummyHash, password);
}
