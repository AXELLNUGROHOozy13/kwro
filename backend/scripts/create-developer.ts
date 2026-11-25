/**
 * One-time bootstrap for the first developer account.
 * Credentials come from environment variables at run time and are never stored in the repo:
 *   DEV_EMAIL=... DEV_NAME=... DEV_PASSWORD=... npm run create-developer
 */
import { PrismaClient } from '@prisma/client';
import { hashPassword } from '../src/lib/password';

async function main() {
  const { DEV_EMAIL, DEV_NAME, DEV_PASSWORD } = process.env;
  if (!DEV_EMAIL || !DEV_NAME || !DEV_PASSWORD) throw new Error('Set DEV_EMAIL, DEV_NAME and DEV_PASSWORD.');
  if (DEV_PASSWORD.length < 12) throw new Error('DEV_PASSWORD must be at least 12 characters.');

  const prisma = new PrismaClient();
  try {
    const email = DEV_EMAIL.toLowerCase();
    if (await prisma.developer.findUnique({ where: { email } })) {
      console.log(`Developer already exists: ${email} (skipped)`);
      return;
    }
    const dev = await prisma.developer.create({
      data: { email, name: DEV_NAME, passwordHash: await hashPassword(DEV_PASSWORD) },
    });
    console.log(`Developer created: ${dev.email}`);
  } finally {
    await prisma.$disconnect();
  }
}

main().catch((e) => {
  console.error(e instanceof Error ? e.message : e);
  process.exit(1);
});
