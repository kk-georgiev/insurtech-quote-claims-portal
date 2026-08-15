# Motor Insurance — Quote & Claims Portal

Учебен InsurTech портал за изчисляване на оферта, издаване на автомобилна
полица и завеждане и обработване на щета (FNOL).

> Текущ етап: **техническа основа за среща с ментора**. Домейн функционалностите
> още не са реализирани. Целта на този branch е екипът да демонстрира избрания
> подход и да валидира важните решения, преди да започне същинската разработка.

## Какво работи в момента

- Spring Boot backend skeleton с health endpoint и PostgreSQL конфигурация;
- React + TypeScript frontend с mentor-checkpoint екран;
- малък frontend → backend поток през `GET /api/v1/system/info`;
- PostgreSQL чрез Docker Compose и начална Flyway миграция;
- отделни backend и frontend CI проверки;
- документация за архитектурата, срещата с ментора и екипната работа.

## Предложен стек

| Област | Избор | Статус |
|---|---|---|
| Backend | Java 21, Spring Boot 4.1, Maven | предложен за потвърждение |
| Frontend | React 19, TypeScript, Vite 8 | предложен за потвърждение |
| Database | PostgreSQL 17, Flyway | предложен за потвърждение |
| Архитектура | модулен монолит в monorepo | предложена за потвърждение |
| API | REST, `/api/v1` | предложен за потвърждение |
| Authentication | все още не е избрана окончателно | отворен въпрос |
| Claim images | local storage за MVP или object storage | отворен въпрос |

Версиите са начална техническа позиция, а не необратимо решение. Отворените
въпроси са събрани в [mentor checkpoint документа](docs/mentor_checkpoint.md).

## Структура

```text
.
├── backend/                 Spring Boot API
├── frontend/                React приложение
├── docs/                    бизнес и техническа документация
├── .github/workflows/       CI проверки
├── docker-compose.yml       локален PostgreSQL
└── .env.example             примерна локална конфигурация
```

Планираните backend модули са `auth`, `customer`, `vehicle`, `tariff`,
`pricing`, `quote`, `policy`, `claim`, `notification` и `shared`. Те ще се
добавят постепенно, когато обхватът им бъде потвърден.

## Стартиране локално

За първо стартиране следвайте стъпките в [START_HERE.md](START_HERE.md).

### Необходими инструменти

- Java 21;
- Maven 3.6.3+;
- Node.js 22.14+ или 24 LTS и npm;
- Docker Desktop или локален PostgreSQL 17.

### 1. Конфигурация и база данни

```bash
cp .env.example .env
docker compose up -d postgres
```

На Windows PowerShell използвайте:

```powershell
Copy-Item .env.example .env
docker compose up -d postgres
```

### 2. Backend

```bash
cd backend
mvn spring-boot:run
```

Backend адрес: `http://localhost:8080`.

### 3. Frontend

В отделен terminal:

```bash
cd frontend
npm install
npm run dev
```

Frontend адрес: `http://localhost:5173`.

## Проверки

```bash
cd backend
mvn verify

cd ../frontend
npm run typecheck
npm run build
```

## Налични технически endpoints

| Метод | Път | Предназначение |
|---|---|---|
| `GET` | `/api/v1/system/info` | демонстрация на frontend/backend връзката |
| `GET` | `/actuator/health` | технически health check |

Тези endpoints не представляват завършена бизнес функционалност.

## Документация

- [Оригинално задание](assignment.md)
- [Бизнес анализ](docs/motor_insurance_portal_business_analysis.md)
- [UML диаграми](docs/uml_diagrams.md)
- [Въпроси към ментора](docs/questions.md)
- [Mentor checkpoint](docs/mentor_checkpoint.md)
- [Development diary](docs/development_diary.md)
- [Правила за работа](docs/contributing.md)

## Следваща стъпка

След срещата с ментора екипът записва решенията в
`docs/mentor_checkpoint.md` и `docs/development_diary.md`, след което започва
първият тънък вертикален процес: клиент и автомобил → оферта → разбивка на
премията. Не започваме паралелно всички модули преди да е потвърден MVP
обхватът.
