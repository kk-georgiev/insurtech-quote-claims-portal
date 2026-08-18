# Frontend

React + TypeScript quote calculator created with Vite. The form calls the
Spring Boot Quote API and renders the saved premium snapshot and factor
breakdown.

## Commands

```bash
npm install
npm run dev
npm run typecheck
npm run build
```

In development, `/api` is proxied to `http://localhost:8080`. Override this
with `VITE_DEV_API_TARGET` when needed.
