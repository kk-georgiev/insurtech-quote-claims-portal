# Development diary

## 2026-08-15 — Initial foundation

### Добавено

- monorepo структура с `backend`, `frontend` и `docs`;
- Spring Boot API skeleton и технически endpoint;
- React/TypeScript mentor-checkpoint екран;
- PostgreSQL чрез Docker Compose;
- начална Flyway миграция;
- CI проверки за backend и frontend;
- бизнес, архитектурна и mentor документация.

### Решения за потвърждение

- модулен монолит;
- REST API с `/api/v1`;
- Java 21 / Spring Boot 4.1;
- React 19 / TypeScript / Vite;
- PostgreSQL 17 и Flyway.

### Следващо

След срещата с ментора се записват отговорите в `mentor_checkpoint.md` и се
избира първият вертикален slice. Не се започват едновременно quote, policy и
claim имплементации.
