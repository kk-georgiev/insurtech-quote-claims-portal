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

## 2026-08-18 — Quote vertical slice v1

### Добавено

- `POST /api/v1/quotes` с Bean Validation и бизнес validation;
- прозрачна демонстрационна тарифа `2026.1-demo`;
- фактори за възраст, стаж, регион, мощност и bonus–malus;
- immutable quote snapshot в PostgreSQL чрез Flyway `V2`;
- `GET /api/v1/quotes/{id}` за прочитане на запазена оферта;
- стандартизирани API грешки за validation и missing quote;
- unit тестове за pricing, service и request validation;
- React форма и визуална разбивка на премията.

### Съзнателно отложено

- production тарифа и административна конфигурация;
- customer/vehicle master data;
- authentication и authorization;
- приемане на оферта и издаване на полица.

### Следващо

След mentor review: потвърждение на тарифата и реализиране на quote acceptance
→ policy snapshot като втори vertical slice.
