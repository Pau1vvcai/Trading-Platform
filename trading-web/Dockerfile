FROM node:22-alpine AS build
WORKDIR /app
ARG API_INTERNAL_URL=http://api:8080
ARG ML_INTERNAL_URL=http://ml-research:8090
ARG NEXT_PUBLIC_WS_URL=ws://localhost:8080/ws/market
ENV API_INTERNAL_URL=$API_INTERNAL_URL
ENV ML_INTERNAL_URL=$ML_INTERNAL_URL
ENV NEXT_PUBLIC_WS_URL=$NEXT_PUBLIC_WS_URL
COPY package*.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
EXPOSE 3000
CMD ["node", "server.js"]
