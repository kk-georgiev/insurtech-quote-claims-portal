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

The application expects PostgreSQL on `localhost:5432` by default. Values can
be overridden through `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`.
