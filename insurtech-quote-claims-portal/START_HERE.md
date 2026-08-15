# START HERE — стартиране на чистото копие

Този архив съдържа завършена **техническа основа**, а не завършен MVP. Не
смесвайте файловете му с вече частично копирана версия на проекта. Най-сигурно
е да използвате чист clone и да копирате в него съдържанието на папката от ZIP.

## 1. Подготовка на чисто локално repository

В PowerShell:

```powershell
cd C:\Programing
git clone https://github.com/kk-georgiev/insurtech-quote-claims-portal.git insurtech-quote-claims-portal-clean
cd .\insurtech-quote-claims-portal-clean
git switch -c chore/initial-project-foundation
```

След това копирайте **съдържанието** на папката
`insurtech-quote-claims-portal` от ZIP върху този чист clone и изберете
`Replace` за съществуващите `README.md` и `.gitignore`. Не копирайте `.git` от
друго repository; архивът умишлено не съдържа `.git`.

## 2. Проверка на структурата

От корена на repository-то:

```powershell
Test-Path .\docker-compose.yml
Test-Path .\backend\pom.xml
Test-Path .\backend\src\main\java\bg\sirma\insurtech\motorinsurance\MotorInsuranceApplication.java
Test-Path .\backend\src\main\resources\application.yml
Test-Path .\backend\src\main\resources\db\migration\V1__create_app_metadata.sql
Test-Path .\frontend\package.json
```

Всичките шест проверки трябва да върнат `True`.

## 3. PostgreSQL

Стартирайте Docker Desktop и изчакайте да покаже, че engine-ът работи. След
това, от корена на repository-то:

```powershell
Copy-Item .env.example .env
docker compose up -d postgres
docker compose ps
```

Изчакайте контейнерът `motor-insurance-postgres` да стане `healthy`.
По подразбиране PostgreSQL е достъпен на host port `5433`, за да не влиза в
конфликт с локално инсталиран PostgreSQL на стандартния порт `5432`.

Ако вече сте създавали локална база със стари credentials и получите
`password authentication failed`, може да нулирате само development volume-а:

```powershell
docker compose down -v
docker compose up -d postgres
```

`down -v` изтрива локалните данни на тази development база.

## 4. Backend

В първи terminal:

```powershell
cd .\backend
mvn clean spring-boot:run
```

Успешният старт завършва с `Started MotorInsuranceApplication`. Проверка:

- http://localhost:8080/actuator/health
- http://localhost:8080/api/v1/system/info

## 5. Frontend

Във втори terminal, от корена на repository-то:

```powershell
cd .\frontend
npm ci
npm run dev
```

Отворете http://localhost:5173. Екранът трябва да покаже `Backend connected`.

## 6. Преди commit

```powershell
git status
git diff --stat
cd .\backend
mvn verify
cd ..\frontend
npm run typecheck
npm run build
```

Едва след като проверите промените, екипът може сам да направи commit и push.
