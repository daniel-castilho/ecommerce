# CI maintenance — GitHub Actions upgrade (Node 20 deprecation)

**Created:** 2026-08-02
**Status:** backlog (low priority — cosmetic, no current impact)

## Issue

`.github/workflows/ci.yml` uses `actions/checkout@v4`, `actions/setup-java@v4` and
`actions/upload-artifact@v4`, which run on Node 20. GitHub is deprecating Node 20 on
runners and is currently forcing these actions to run on Node 24 (build-time
annotation warning, no effect on results).

## Action needed

Bump the actions in `.github/workflows/ci.yml` to the next major that targets a
current Node runtime (e.g. `v4` → `v5`). Re-run the pipeline after the bump.

## Acceptance

- [ ] No "Node.js 20 is deprecated" annotation on a fresh CI run
- [ ] Both jobs (`unit-and-archunit`, `integration-tests`) still green
