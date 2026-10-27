const api = process.env.API_INTERNAL_URL ?? "http://localhost:8080";
const ml = process.env.ML_INTERNAL_URL ?? "http://localhost:8090";
/** @type {import('next').NextConfig} */
const config = {
  output: "standalone",
  async rewrites() { return [
    { source: "/api/:path*", destination: `${api}/api/:path*` },
    { source: "/ml-api/:path*", destination: `${ml}/:path*` }
  ]; }
};
export default config;
