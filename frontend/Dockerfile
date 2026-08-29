# Multi-stage build: Node build stage runs `npm run build`, which is copied
# into an nginx stage that serves the static SPA (no Node toolchain ships in
# the final image). Story 4.1 - see _bmad-output/implementation-artifacts/
# spec-4-1-backend-and-frontend-dockerfiles.md.

# --- Build stage ---
FROM node:20-alpine AS build
WORKDIR /build

# Copy manifests first so `npm ci` is cached across rebuilds that only
# change source files.
COPY package.json package-lock.json* ./
RUN npm ci

COPY . .

# Vite inlines `import.meta.env.VITE_API_URL` at build time (see
# frontend/vite.config.ts and frontend/src/api/client.ts) - a runtime env
# var would not work, so it must be supplied as a build ARG.
ARG VITE_API_URL
ENV VITE_API_URL=${VITE_API_URL}

RUN npm run build

# --- Runtime stage ---
FROM nginx:alpine AS runtime

COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
