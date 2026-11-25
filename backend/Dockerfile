# KAWREXAM backend. Works on Railway and with docker compose.
FROM node:22-slim
RUN apt-get update -y && apt-get install -y --no-install-recommends openssl ca-certificates \
  && rm -rf /var/lib/apt/lists/*
WORKDIR /app
RUN chown node:node /app
USER node
ENV PRISMA_HIDE_UPDATE_MESSAGE=1

COPY --chown=node:node package*.json ./
# Switch to `npm ci` once package-lock.json is committed.
RUN npm install
COPY --chown=node:node . .
RUN npm run build

ENV NODE_ENV=production
EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD node -e "fetch('http://localhost:'+(process.env.PORT||3000)+'/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
CMD ["sh", "docker-entrypoint.sh"]
