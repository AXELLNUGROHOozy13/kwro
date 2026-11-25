/** @type {import('next').NextConfig} */
const nextConfig = {
  poweredByHeader: false,
  // Rewrites are resolved at BUILD time. On Vercel, set BACKEND_URL in Project Settings -> Environment Variables
  // BEFORE deploying (and redeploy after changing it). The browser only talks to this origin; /api is proxied to
  // the backend, so the refresh cookie stays same-site and CORS is not needed.
  async rewrites() {
    const backend = (process.env.BACKEND_URL ?? 'http://localhost:3000').replace(/\/+$/, '');
    return [{ source: '/api/:path*', destination: `${backend}/api/:path*` }];
  },
};
export default nextConfig;
