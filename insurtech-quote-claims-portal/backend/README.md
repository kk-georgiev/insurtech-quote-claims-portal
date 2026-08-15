# Backend

Spring Boot API skeleton for the Motor Insurance portal.

## Current responsibility

The backend currently exposes only technical checkpoint endpoints. Domain
modules will be introduced one vertical slice at a time after mentor validation.

## Commands

```bash
mvn spring-boot:run
mvn verify
```

The application expects the Docker PostgreSQL instance on host port
`localhost:5433` by default (container port `5432`). Values can
be overridden through `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`.
