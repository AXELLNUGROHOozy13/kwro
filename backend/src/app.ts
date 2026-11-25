import cookieParser from 'cookie-parser';
import cors from 'cors';
import express from 'express';
import helmet from 'helmet';
import { corsOrigins } from './config/env';
import { prisma } from './lib/db';
import { errorHandler, notFoundHandler } from './middleware/error';
import { authRouter } from './modules/auth/auth.routes';
import { developerRouter } from './modules/developer/developer.routes';

export function createApp() {
  const app = express();
  app.set('trust proxy', 1); // behind Railway / Nginx: correct client IP for rate limiting and audit logs
  app.disable('x-powered-by');
  app.use(helmet());
  if (corsOrigins.length > 0) app.use(cors({ origin: corsOrigins, credentials: true }));
  app.use(express.json({ limit: '1mb' }));
  app.use(cookieParser());

  app.get('/health', async (_req, res) => {
    try {
      await prisma.$queryRaw`SELECT 1`;
      res.json({ status: 'ok' });
    } catch {
      res.status(503).json({ status: 'degraded', database: 'unreachable' });
    }
  });

  // API v1. Admin, student, exam, and monitoring modules mount here in later phases.
  app.use('/api/v1/auth', authRouter);
  app.use('/api/v1/developer', developerRouter);

  app.use(notFoundHandler);
  app.use(errorHandler);
  return app;
}
