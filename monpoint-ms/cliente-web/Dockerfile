# syntax=docker/dockerfile:1
FROM node:22-bookworm-slim AS build
WORKDIR /app

COPY package.json package-lock.json ./
# CA opcional para entornos con proxy HTTPS. No queda en ninguna capa.
RUN --mount=type=secret,id=proxy_ca \
    if [ -f /run/secrets/proxy_ca ]; then export NODE_EXTRA_CA_CERTS=/run/secrets/proxy_ca; fi; \
    npm ci --no-audit --no-fund

COPY . .
# Vite incorpora esta URL pública al JavaScript. No admite secretos.
ARG VITE_GATEWAY_URL=http://localhost:8080
RUN VITE_GATEWAY_URL="$VITE_GATEWAY_URL" npm run build

FROM nginx:1.28-alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1/healthz || exit 1
