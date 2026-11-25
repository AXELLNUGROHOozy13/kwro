// pm2 process file. Run from the repo root:  pm2 start ecosystem.config.cjs
module.exports = {
  apps: [
    {
      name: 'kawrexam-api',
      cwd: './backend',
      script: 'dist/server.js',
      node_args: '--env-file=.env', // loads backend/.env (needs Node 20.6+)
      max_memory_restart: '400M',
      env: { NODE_ENV: 'production' },
    },
    {
      name: 'kawrexam-console',
      cwd: './developer-console',
      script: 'npm',
      args: 'start',
      max_memory_restart: '400M',
      env: { NODE_ENV: 'production', PORT: 3001 },
    },
  ],
};
