import { createApp } from './app';
import { env } from './config/env';
import { prisma } from './lib/db';

const server = createApp().listen(env.PORT, () => {
  console.log(`KAWREXAM backend listening on :${env.PORT} (${env.NODE_ENV})`);
});

// Graceful shutdown so in-flight requests finish and DB connections close cleanly.
for (const signal of ['SIGINT', 'SIGTERM'] as const) {
  process.on(signal, () => {
    server.close(async () => {
      await prisma.$disconnect();
      process.exit(0);
    });
  });
}
