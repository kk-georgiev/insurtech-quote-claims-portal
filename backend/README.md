# Backend

Spring Boot 4.1.1 / Java 21 / Maven. Modular monolith, package-by-feature
(`com.motorinsurance.{module}.{api,application,domain,persistence}`). This
milestone only the `shared` module exists (AD-1/AD-6) - no business logic yet.

## Prerequisites

- JDK 21
- Maven 3.9+ (or your IDE's bundled Maven)
- Docker + Docker Compose (for local Postgres)

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

On startup, Flyway runs `src/main/resources/db/migration/V1__baseline.sql`
against the database. If Postgres is unreachable, the app fails fast with a
clear error in the logs instead of starting in a broken state.

## Project layout

```text
src/main/java/com/motorinsurance/
  MotorInsuranceApplication.java   # Spring Boot entry point
  shared/
    api/      # ApiError envelope (AD-7) + GlobalExceptionHandler
    config/   # base Spring config (e.g. dev CORS for the Vite dev server)
src/main/resources/
  application.yml
  db/migration/   # Flyway migrations, V{n}__description.sql
```

`auth`, `quote`, and `pricing` module packages do not exist yet - each is
created in the story that first needs it (AD-6).
