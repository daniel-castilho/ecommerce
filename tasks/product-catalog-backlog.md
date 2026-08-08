# Product Catalog — Backlog Status

**Companion documents:**
`product-catalog-module-spec.md` · `product-catalog-implementation-sequence.md`

**Epic goal:** Production-viable catalog — searchable, image-capable, admin-manageable — on hexagonal architecture, integrated with checkout inventory reservation.

> **Epic S1–S9 delivered** (2026-07-31 / storefront polish through early August 2026).
> Later additions (inventory reservation, archive/reactivate, product-detail, scheduled expiry) are listed below.

---

## Current Status Summary

| Story / area                     | Status                | Notes                                                                      |
| -------------------------------- | --------------------- | -------------------------------------------------------------------------- |
| S1 Domain model                  | ✅ Done               | `Product`, `Sku`, `Slug`, `Money`, status machine, pure unit tests         |
| S2 JPA persistence               | ✅ Done               | Entity + mapper + adapter; `@Version` round-trip (lesson #1)               |
| S3 Search + uniqueness           | ✅ Done               | Criteria search, pagination, SKU/slug guards; ARCHIVED excluded by default |
| S4 Category tree + cache         | ✅ Done               | Nested tree, `@ApplicationScoped` cache with invalidation                  |
| S5 Publish workflow              | ✅ Done               | Guards: price, image+alt, category existence                               |
| S6 Image storage (S3/LocalStack) | ✅ Done               | `ProductImageStorageS3Adapter`; LocalStack 3.8.1; bootstrap script         |
| S7 Admin CRUD UI                 | ✅ Done               | Create / edit / publish; image upload; `@RolesAllowed("ADMIN")`            |
| S8 Public catalog UI             | ✅ Done               | Search, filter, pagination; product cards → detail page                    |
| S9 Stock decrement               | ✅ Done               | Atomic decrement; used by checkout                                         |
| Inventory reservation            | ✅ Done               | Port + table + TTL; wired from order-checkout (v0.2.0 era)                 |
| Scheduled reservation expiry     | ✅ Done               | Daemon sweep every 60s                                                     |
| Archive / reactivate             | ✅ Done               | Admin path; `ARCHIVED → ACTIVE` allowed (v0.6.0)                           |
| Product-detail page              | ✅ Done               | Gallery, price, stock, buy link; reviews section later (v0.10.0)           |
| Postgres Full-Text Search        | ⚠️ Optional / planned | Criteria API today; FTS docs may exist under `tasks/` if started           |

---

## Implemented (original S1–S9)

### Foundation

- **S1** — Framework-free domain; status transitions; image primary invariant
- **S2** — `ProductJpaEntity` / mapper / adapter; Flyway catalog extension (`V7` era)
- **S3** — `search()`, `existsBySku` / `existsBySlug`, pagination, filters

### Categories & publish

- **S4** — Category hierarchy + cache invalidation on mutation
- **S5** — Publish only when validation passes; stays DRAFT otherwise

### Media & UI

- **S6** — S3-compatible storage; public URLs; LocalStack for dev/CI
- **S7** — Admin manage-product flow (QA’d on Open Liberty + LocalStack)
- **S8** — Public catalog + **product-detail** by slug (ACTIVE only)

### Stock

- **S9** — `decrementStock` with concurrency-safe behaviour for checkout

### Architecture

- `ProductHexagonalArchitectureTest` (8 ArchUnit rules) green

---

## Later additions (post S1–S9)

| Feature                 | Where                        | Notes                                                                |
| ----------------------- | ---------------------------- | -------------------------------------------------------------------- |
| Inventory reservation   | product-catalog ports + JPA  | reserve / confirm / release; atomic stock; idempotent reservation id |
| Reservation expiry job  | `ReservationExpiryScheduler` | Proactive free of expired holds                                      |
| Archive / reactivate    | Domain + admin UI            | Soft-remove from storefront; reactivation path                       |
| Design-system alignment | web                          | Cards, badges, tokens on catalog/manage pages                        |
| Reviews on detail page  | product-reviews              | Composition only; catalog unchanged at domain level                  |

---

## Explicit debt / optional next work

- Richer search ranking (PostgreSQL FTS / `tsvector`) if catalog size demands it
- Subcategory rollup in search filters (direct category assignment only today)
- Bulk CSV import of products (never in original epic)
- Real S3 (non-LocalStack) configuration for non-dev environments — env-driven, already supported by adapter

---

## How the module is structured today

```
com.loja.productcatalog/
├── domain/model + port/in + port/out + exception
├── application/service + application/dto
└── adapter/
    ├── in/web/           → ManageProductBean, catalog/detail beans
    └── out/persistence/  → JPA + CategoryTreeCache
    └── out/storage/      → S3 / LocalStack image adapter
```

Cross-module:

- **order-checkout** consumes stock / reservation ports
- **admin-dashboard** composes product list/create/edit UI via use cases
- **product-reviews** looks up products via ports only

---

## Definition of Done (Epic)

- [x] Searchable public catalog + product detail
- [x] Admin create / image upload / publish
- [x] Categories with cache
- [x] Stock decrement + inventory reservation
- [x] ArchUnit + unit/IT coverage
- [x] No `javax.*` in module domain/application

---

_This backlog is a living status document. For the original INVEST stories (Given/When/Then, story points, full DoR/DoD), see the git history of this file._

```

```
