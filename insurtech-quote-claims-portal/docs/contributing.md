# Правила за екипна работа

## Branches

- Не се разработва директно в `main`.
- Примерни имена: `feat/quote-calculation`, `fix/claim-validation`,
  `docs/mentor-decisions`, `chore/initial-project-foundation`.
- Един branch и един pull request трябва да имат ясна, ограничена цел.

## Преди pull request

```bash
cd backend
mvn verify

cd ../frontend
npm ci
npm run typecheck
npm run build
```

Проверете `git status` и `git diff`, за да не попаднат `.env`, `target`,
`node_modules`, IDE файлове или временни архиви.

## Commit съобщения

Използвайте кратки съобщения, например:

- `feat: add quote calculation endpoint`
- `fix: validate claim incident date`
- `docs: record mentor architecture decisions`
- `chore: add initial project foundation`

## Pull request

Опишете какво е променено, как е проверено и кои решения все още са отворени.
Поне един друг член на екипа преглежда промените преди merge.
