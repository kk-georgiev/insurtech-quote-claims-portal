# Backend

Spring Boot API for the Motor Insurance portal.

## Current responsibility

The first domain module implements a persistent, versioned motor quote with
input validation and a transparent premium breakdown. The coefficients in
`2026.1-demo` are placeholders for mentor validation.

## Quote endpoints

- `POST /api/v1/quotes` — calculate and save a quote;
- `GET /api/v1/quotes/{id}` — retrieve the saved quote snapshot.

See [`docs/quote_pricing_v1.md`](../docs/quote_pricing_v1.md) for the formula.

## Commands

```bash
mvn spring-boot:run
mvn verify
```

The application expects the Docker PostgreSQL instance on host port
`localhost:5433` by default (container port `5432`). Values can
be overridden through `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`.
