# Начални UML/домейн диаграми

Диаграмите са работна хипотеза за обсъждане с ментора.

## Контекст и основен поток

```mermaid
flowchart LR
    Customer[Клиент] --> Portal[Web Portal]
    Agent[Агент] --> Portal
    Adjuster[Ликвидатор] --> Portal
    Portal --> API[Spring Boot API]
    API --> DB[(PostgreSQL)]
    API --> Files[Claim attachments]

    CustomerData[Клиент и автомобил] --> Quote[Оферта]
    Quote --> Policy[Полица]
    Policy --> Claim[Щета / FNOL]
    Claim --> Review[Преглед]
    Review --> Decision[Одобрение или отказ]
```

## Предложени домейн връзки

```mermaid
classDiagram
    Customer "1" --> "0..*" Vehicle
    Customer "1" --> "0..*" Quote
    Vehicle "1" --> "0..*" Quote
    Quote "1" --> "0..1" Policy
    Policy "1" --> "0..*" Claim
    Claim "1" --> "0..*" ClaimAttachment
    Claim "0..*" --> "0..1" Adjuster

    class Quote {
      status
      premium
      validUntil
      pricingBreakdown
    }
    class Policy {
      policyNumber
      startsAt
      endsAt
      termsSnapshot
    }
    class Claim {
      claimNumber
      incidentDate
      status
      approvedAmount
    }
```

Имената, cardinality и lifecycle правилата трябва да се потвърдят преди
създаване на production entities.
