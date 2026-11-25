/** @type {import('next').NextConfig} */
const nextConfig = {
  poweredByHeader: false,
  output: process.env.NEXT_OUTPUT === 'standalone' ? 'standalone' : undefined,
  // NOTE: rewrites are resolved at BUILD time, so BACKEND_URL must be set when the image is built.
  // The browser only ever talks to this origin; /api is proxied to the backend.
  // That keeps the refresh cookie same-site (SameSite=Strict) and avoids CORS.
  async rewrites() {
    const backend = process.env.BACKEND_URL ?? 'http://localhost:3000';
    return [{ source: '/api/:path*', destination: `${backend}/api/:path*` }];
  },
};
export default nextConfig;
