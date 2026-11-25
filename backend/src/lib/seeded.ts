import { createHash } from 'node:crypto';

// Deterministic PRNG (mulberry32) seeded from a string, so a student's order never changes between app restarts.
function rngFromSeed(seed: string): () => number {
  let a = createHash('sha256').update(seed).digest().readUInt32LE(0);
  return () => {
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** Fisher-Yates shuffle with a seeded RNG. Same seed + same input = same output. */
export function seededShuffle<T>(items: readonly T[], seed: string): T[] {
  const arr = [...items];
  const rand = rngFromSeed(seed);
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(rand() * (i + 1));
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr;
}
