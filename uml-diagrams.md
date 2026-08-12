<!-- title: UML диаграми — Motor Insurance Portal -->

# UML диаграми — Motor Insurance Quote & Claims Portal

Изведени изцяло от `Motor-Insurance-Portal-Business-Analysis.md` (раздели 4–11) и `questions.md`. Няма добавени данни от други документи в репото.

---

## 1. Диаграма на класовете (Domain Model)

Източник: раздел 11 (Модел на базата данни) + връзките, описани в разделите за оферти (7), тарифи (6) и щети (8).

```mermaid
classDiagram
    class User {
      +UUID id
      +String email
      +String passwordHash
      +Role role
      +String status
    }
    class Role {
      <<enumeration>>
      CLIENT
      AGENT
      LIQUIDATOR
      ADMINISTRATOR
    }
    class CustomerProfile {
      +String firstName
      +String lastName
      +LocalDate dateOfBirth
      +LocalDate licenseIssueDate
    }
    class Vehicle {
      +String vin
      +String registrationNumber
      +String make
      +String model
      +Number powerKw
      +String region
    }
    class TariffVersion {
      +String version
      +LocalDate validFrom
      +LocalDate validTo
      +String status
      +BigDecimal basePremium
    }
    class TariffFactor {
      +String type
      +Number minValue
      +Number maxValue
      +BigDecimal factor
    }
    class Quote {
      +BigDecimal totalPremium
      +String currency
      +LocalDate validUntil
      +Json snapshot
      +Json breakdown
      +String status
    }
    class Policy {
      +String policyNumber
      +LocalDate coverageStart
      +LocalDate coverageEnd
      +BigDecimal premium
      +Json snapshot
      +String status
    }
    class Claim {
      +String referenceNumber
      +LocalDate incidentDate
      +String status
      +BigDecimal approvedAmount
      +BigDecimal paidAmount
    }
    class ClaimAttachment {
      +String storageKey
      +String mimeType
      +Number size
      +String hash
    }
    class ClaimStatusHistory {
      +String fromStatus
      +String toStatus
      +String reason
      +Instant timestamp
    }
    class Notification {
      +String type
      +String entityReference
      +Instant readAt
    }
    class AuditLog {
      +String action
      +String entity
      +Instant timestamp
    }

    User "1" --> "0..1" CustomerProfile : профил (роля CLIENT)
    User --> Role
    CustomerProfile "1" --> "*" Vehicle : притежава
    Vehicle "1" --> "*" Quote : остойностен в
    TariffVersion "1" --> "*" TariffFactor : дефинира
    TariffVersion "1" --> "*" Quote : ползвана от
    Quote "1" --> "0..1" Policy : приета в
    Policy "1" --> "*" Claim : покрива
    Claim "1" --> "*" ClaimAttachment : прикачени файлове
    Claim "1" --> "*" ClaimStatusHistory : история
    User "1" --> "*" Claim : ликвидатор преглежда
    User "1" --> "*" Notification : получава
    User "1" --> "*" AuditLog : действия (actor)
```

---

## 2. Диаграма на use case-овете

Източник: раздел 4 (Потребители и права) + раздел 12 (Frontend структура и екрани).

```mermaid
flowchart LR
    Client(["Клиент"])
    Agent(["Агент"])
    Liquidator(["Ликвидатор"])
    Admin(["Администратор"])

    subgraph UC_Client["Клиентски use cases"]
        direction TB
        uc1(("Регистрация / вход"))
        uc2(("Управление на автомобили"))
        uc3(("Заявка на оферта"))
        uc4(("Приемане на оферта"))
        uc5(("Преглед на полици"))
        uc6(("Завеждане на щета — FNOL"))
        uc7(("Качване на снимки"))
        uc8(("Проследяване на статус на щета"))
        uc9(("Преглед на известия"))
    end

    subgraph UC_Agent["Агентски use cases"]
        direction TB
        uc10(("Оферта от името на клиент"))
        uc11(("Издаване на полица от името на клиент"))
        uc12(("Търсене на клиент"))
    end

    subgraph UC_Liquidator["Ликвидаторски use cases"]
        direction TB
        uc13(("Опашка от щети + филтри"))
        uc14(("Преглед на щета и снимки"))
        uc15(("Одобрение / отказ на щета"))
        uc16(("Въвеждане на изплатена сума"))
    end

    subgraph UC_Admin["Администраторски use cases"]
        direction TB
        uc17(("Управление на тарифни версии"))
        uc18(("Активиране на тарифна версия"))
        uc19(("Управление на потребители и роли"))
        uc20(("Преглед на audit log"))
    end

    Client --- uc1 & uc2 & uc3 & uc4 & uc5 & uc6 & uc7 & uc8 & uc9
    Agent --- uc10 & uc11 & uc12
    Liquidator --- uc13 & uc14 & uc15 & uc16
    Admin --- uc17 & uc18 & uc19 & uc20
```

> Раздел 4 отбелязва изрично: ролята **агент не е напълно дефинирана** в заданието — обхватът на use case-овете `uc10`–`uc12` е за уточняване на екипната среща (виж и `questions.md`, т. 9 за процеса на работа).

---

## 3. Диаграми на състоянията

### 3.1 Оферта (Quote) — раздел 7.1

```mermaid
stateDiagram-v2
    [*] --> CALCULATED
    CALCULATED --> ACCEPTED : приемане (валидна + без издадена полица)
    CALCULATED --> EXPIRED : изтича validUntil
    CALCULATED --> CANCELLED
    ACCEPTED --> [*]
    EXPIRED --> [*]
    CANCELLED --> [*]
```

### 3.2 Полица (Policy) — раздел 7.2

```mermaid
stateDiagram-v2
    [*] --> SCHEDULED
    SCHEDULED --> ACTIVE : настъпва coverageStart
    ACTIVE --> EXPIRED : отминава coverageEnd
    SCHEDULED --> CANCELLED
    ACTIVE --> CANCELLED
    EXPIRED --> [*]
    CANCELLED --> [*]
```

### 3.3 Щета (Claim) — раздели 8.3 и 8.4

```mermaid
stateDiagram-v2
    [*] --> SUBMITTED
    SUBMITTED --> UNDER_REVIEW : start-review
    SUBMITTED --> WITHDRAWN : (по избор)
    UNDER_REVIEW --> NEEDS_MORE_INFORMATION : (по избор)
    NEEDS_MORE_INFORMATION --> UNDER_REVIEW
    UNDER_REVIEW --> APPROVED : approve (изисква положителна сума)
    UNDER_REVIEW --> REJECTED : reject (изисква причина)
    APPROVED --> PAID : mark-paid
    REJECTED --> [*]
    PAID --> [*]
    WITHDRAWN --> [*]
```

> Директен преход `SUBMITTED → PAID` е забранен по дизайн (раздел 8.4 и раздел 16.3 — тестова стратегия изрично проверява това).

### 3.4 Тарифна версия (TariffVersion) — раздел 6.5

```mermaid
stateDiagram-v2
    [*] --> DRAFT
    DRAFT --> ACTIVE : активиране
    ACTIVE --> RETIRED : нова версия става ACTIVE
    RETIRED --> [*]
```

> След `ACTIVE` версията не се редактира директно — промяна винаги създава нова `DRAFT` версия (раздел 6.5).

---

## 4. Диаграма на последователността — цялостен бизнес процес

Източник: раздел 2 (Разяснение на заданието), раздел 5 (Основен бизнес процес), раздел 7.3 (Приемане на оферта) и раздел 9 (Известия).

```mermaid
sequenceDiagram
    actor C as Клиент
    participant BE as Backend (Quote/Policy/Claim)
    participant DB as PostgreSQL
    actor L as Ликвидатор

    C->>BE: POST /quotes (водач, автомобил)
    BE->>DB: lookup активна TariffVersion
    BE-->>C: Quote {CALCULATED, breakdown}

    C->>BE: POST /quotes/{id}/accept
    BE->>DB: транзакция: собственост + не изтекла + без полица
    BE->>DB: Quote → ACCEPTED, генерира Policy (sequence + UNIQUE)
    BE-->>C: Policy {SCHEDULED/ACTIVE, номер}

    Note over C,BE: настъпва инцидент

    C->>BE: POST /claims (полица, дата, описание)
    C->>BE: POST /claims/{id}/attachments (снимки)
    BE-->>C: Claim {SUBMITTED, референтен номер}
    BE->>DB: публикува ClaimStatusChanged → Notification

    L->>BE: GET /claims?status=SUBMITTED
    L->>BE: POST /claims/{id}/start-review
    BE->>DB: ClaimStatusHistory += UNDER_REVIEW, Notification

    L->>BE: POST /claims/{id}/approve {amount} или /reject {reason}
    BE->>DB: ClaimStatusHistory += APPROVED/REJECTED, Notification
    BE->>DB: (ако APPROVED) mark-paid → Claim PAID, Notification

    BE-->>C: GET /notifications (poll) — статус на щетата
```

---

## 5. Диаграма на пакетите — модулен монолит

Източник: раздел 10.1 (Препоръчана архитектура).

```mermaid
flowchart TB
    subgraph backend["backend/ (Spring Boot, модулен монолит)"]
        auth[auth]
        customer[customer]
        vehicle[vehicle]
        pricing[pricing]
        tariff[tariff]
        quote[quote]
        policy[policy]
        claim[claim]
        notification[notification]
        shared[shared]
    end

    quote -->|чете тарифа| tariff
    quote -->|чете фактори| pricing
    quote -->|автомобил| vehicle
    quote -->|клиент| customer
    policy -->|приета оферта| quote
    claim -->|активна полица| policy
    claim -->|публикува ClaimStatusChanged| notification
    auth -.->|роли/authorization| quote
    auth -.->|роли/authorization| policy
    auth -.->|роли/authorization| claim
    shared -.->|базови entity/exceptions| quote
    shared -.->|базови entity/exceptions| policy
    shared -.->|базови entity/exceptions| claim
```

> Плътните стрелки = директна бизнес зависимост; пунктираните = напречни (cross-cutting) зависимости от `auth`/`shared`, ползвани от всеки модул.
