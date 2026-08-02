# Release v0.X.0 — <short milestone title>

**Date:** YYYY-MM-DD
**Git tag:** `v0.X.0`
**Baseline commit:** `<short-sha>`
**DoD met:** yes / no (if no, do not tag)

---

## Summary

One paragraph: what this milestone stabilizes (e.g. published catalog listing + cart add,
order checkout MVP).

## In scope (delivered)

- [ ] …
- [ ] …
- [ ] …

## Out of scope / explicit debt

- … (e.g. Jakarta Security, product-detail page, order notifications)

## How to validate

```bash
# Fast check of the touched modules (no Testcontainers):
mvn -q -pl <module> test-compile

# Full build (final WAR):
mvn clean package -pl web -am

# Run (Open Liberty, https://localhost:9443/web/):
./scripts/run-liberty.sh

# LocalStack + Postgres if the release touches product-catalog:
docker compose -f docker/docker-compose.yaml up -d localstack db
./scripts/bootstrap-localstack.sh
```

Smoke path:

1. …
2. …

## Modules touched

| Module | Notes |
|--------|-------|
| shared-kernel | |
| user-account | |
| product-catalog | |
| order-checkout | |
| admin-dashboard | |
| web | |
| docs/ | |

## Lessons promoted

- One-liner or link from `docs/lessons.md` if something new was learned.

## Rollback

```bash
git checkout v0.X.0
# or previous tag: git checkout v0.Y.0
```

---

*Template: copy to `docs/releases/v0.X.0.md`, fill it, then
`git tag -a v0.X.0 -m "v0.X.0 — <short title>"`.*
