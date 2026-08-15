# ADR-0001: Initial technical foundation

- Status: **Proposed — pending mentor confirmation**
- Date: 2026-08-15
- Owners: project team

## Context

The team needs a demonstrable, low-risk starting point before business details
such as authentication, agent permissions and FNOL ownership are confirmed.
Starting all domain modules now would turn assumptions into code and make the
mentor discussion harder rather than easier.

## Proposed decision

- Keep backend and frontend in one repository.
- Use a modular Spring Boot monolith instead of microservices.
- Use Java 21, Spring Boot 4.1, React 19, TypeScript, Vite 8 and PostgreSQL.
- Version the REST API under `/api/v1`.
- Manage database changes only through Flyway.
- Deliver vertical slices in order: Quote, Policy, Claim, Notifications.
- Keep authentication and attachment storage deliberately undecided until the
  mentor meeting.

## Why this is reversible

The first implementation contains only a technical system endpoint, database
connectivity and a presentation screen. There are no domain entities, role
guards or pricing rules to migrate if the mentor recommends a different model.

## Consequences

### Positive

- The team can validate the entire local toolchain and CI immediately.
- Frontend and backend agree on a first API convention.
- Open business decisions remain visible.
- Each later module can be reviewed independently.

### Trade-offs

- This checkpoint is not a business MVP.
- PostgreSQL must run for the full backend application to start.
- Authentication and file uploads cannot be demonstrated yet.

## Confirmation checklist

- [ ] Mentor confirms or changes the stack.
- [ ] Mentor confirms modular monolith and monorepo.
- [ ] Mentor confirms API versioning approach.
- [ ] Mentor chooses authentication direction.
- [ ] Mentor clarifies roles and FNOL ownership.
- [ ] Team changes this ADR status to `Accepted`, `Superseded` or `Rejected`.
