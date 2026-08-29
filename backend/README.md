# Backend

Spring Boot 4.1.1 / Java 21 / Maven. Modular monolith, package-by-feature
(`com.motorinsurance.{module}.{api,application,domain,persistence}`).
Modules so far (AD-1/AD-6): `shared`, `auth` (Stories 1.2-1.4), `pricing`
and `quote` (Story 1.5).

## Prerequisites

- JDK 21
- Maven 3.9+ (or your IDE's bundled Maven)
- Docker + Docker Compose (for local Postgres)

Prefer to run the whole stack in containers instead? See the root README's
[One-command alternative](../README.md#getting-started) (`docker compose up`)
— it builds and starts postgres, backend, and frontend together, no local
JDK/Maven/Node toolchain needed.

## Run natively against a containerized Postgres

1. From the repo root, start Postgres only:

   ```bash
   docker compose up postgres
   ```

2. From `backend/`, run the app:

   ```bash
   cd backend
   mvn spring-boot:run
   ```

   `application.yml` reads its Postgres connection from `POSTGRES_HOST` /
   `POSTGRES_PORT` / `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD`,
   defaulting to the same insecure local-dev values `docker-compose.yml`
   falls back to (`localhost:5432/motorinsurance`, `postgres`/`postgres`), so
   this works with no extra setup. To use different values, copy
   `../.env.example` to `../.env`, adjust it, and export those variables into
   your shell before running `mvn spring-boot:run` (Spring Boot does not load
   `.env` files itself).

3. Verify:

   ```bash
   curl http://localhost:8080/actuator/health
   # {"status":"UP"}
   ```

On startup, Flyway runs every migration under
`src/main/resources/db/migration/` against the database. If Postgres is
unreachable, the app fails fast with a clear error in the logs instead of
starting in a broken state.

One of those migrations (`V5__seed_staff_accounts.sql`) seeds the AGENT,
LIQUIDATOR and ADMINISTRATOR demo accounts, since self-registration only ever
creates CLIENT users. Their credentials are documented in one place only —
the root README's [Demo accounts](../README.md#demo-accounts) section.

## Run tests

```bash
mvn test
```

Needs Docker running: every test class touching a database (`auth`,
`pricing`, `quote`) uses Testcontainers to spin up a throwaway Postgres
automatically (real Flyway migrations, real constraints - no H2
approximation) - no need for `docker compose up postgres` to already be
running first. `JwtServiceTest` is the one exception: a plain unit test
with no Spring context or database at all.

## Project layout

```text
src/main/java/com/motorinsurance/
  MotorInsuranceApplication.java   # Spring Boot entry point
  shared/
    api/      # ApiError envelope (AD-7) + GlobalExceptionHandler
    config/   # base Spring config (e.g. dev CORS for the Vite dev server)
  auth/
    api/, application/, domain/, persistence/   # registration, login, JWT
  pricing/
    domain/, persistence/, application/   # tariff data + PricingService (AD-2 sole entry point)
  quote/
    api/, application/, domain/, persistence/   # POST /api/v1/quotes (calculates + persists), GET /api/v1/quotes/{id} (owner-scoped)
src/main/resources/
  application.yml
  db/migration/   # Flyway migrations, V{n}__description.sql
```
