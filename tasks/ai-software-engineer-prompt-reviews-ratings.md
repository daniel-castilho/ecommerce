---

### 1. `ai-software-engineer-prompt-reviews-ratings.md`

```markdown
# AI Software Engineer Prompt — Reviews & Ratings Module Implementation

**Context:** You are an AI Software Engineer tasked with implementing the Reviews & Ratings module for the existing Jakarta EE 11 + Jakarta Faces modular monolith (`daniel-castilho/ecommerce`). The project already has mature modules (shared-kernel, user-account, product-catalog, order-checkout, admin-dashboard, web) that serve as the single source of truth for architecture, coding standards, testing, security and UI patterns.

**Your primary responsibility:** Implement a complete, production-grade Reviews & Ratings module following the three companion documents below. The goal is to allow verified (or optionally anonymous) customers to leave star ratings + text reviews on products, display them on the public product-detail page, and give administrators a moderation queue.

**Working directory:** Repository root. You will create a **new Maven module** `product-reviews` and extend `web` (JSF pages + beans) and `admin-dashboard` (moderation UI) only where necessary. Never touch existing domain logic of other modules except through their public ports.

**Reference documents (READ THESE FIRST, IN THIS ORDER):**
1. `reviews-ratings-module-spec.md` — Complete technical design (what to build)
2. `reviews-ratings-backlog.md` — INVEST stories with acceptance criteria
3. `reviews-ratings-implementation-sequence.md` — Exact step-by-step build order
4. Existing modules (especially `product-catalog` and `user-account`) + `AGENTS.md` + `docs/design-system.md` + `docs/lessons.md` + `java-jakarta-ee-coding-standards.md` (if present)

**Architecture mandate (non-negotiable):**
- ✅ Hexagonal architecture exactly as used in product-catalog / user-account
- ✅ New module `product-reviews` with the classic layout:
  ```
  domain/model + domain/port/in + domain/port/out + domain/exception
  application/service + application/dto
  adapter/in/web + adapter/out/persistence
  ```
- ✅ Zero framework imports (`jakarta.*`, `javax.*`, Hibernate, etc.) inside `domain/` and `application/`
- ✅ Cross-module dependencies **only** via ports (never import another module’s adapter or application classes)
- ✅ CDI only (`@ApplicationScoped` + `@Transactional`), zero `@EJB` / `@Stateless`
- ✅ 100 % `jakarta.*`
- ✅ English only (code, comments, Javadoc, commits, docs, logs)
- ✅ Design-system tokens only (no hardcoded colors/px)
- ✅ ArchUnit rules must pass
- ✅ RBAC: public pages open, moderation pages `@RolesAllowed("ADMIN")`
- ✅ No new Maven dependencies without explicit human approval

**Pre-Implementation Checklist (Do This First)**
- [ ] Confirm `product-reviews` will be a new top-level Maven module and added to root `pom.xml`
- [ ] Confirm verified-purchase check will use `order-checkout`’s existing ports (or a new thin port if needed)
- [ ] Confirm moderation lives inside `admin-dashboard` (composition only) or as admin pages inside `web`
- [ ] Confirm Flyway migration numbering (next free Vxx)
- [ ] Confirm design-system tokens for stars, status badges, cards already exist or request new ones

**If any of the above is unclear, STOP and ask the human. Do not guess.**

**Implementation Order:** Follow the 12 steps in `reviews-ratings-implementation-sequence.md` strictly. Do not skip, reorder or parallelize without permission.

**Mandatory Code Quality Checklist (every file):**
- [ ] Zero framework imports in domain/application
- [ ] Ports are interfaces; adapters implement them
- [ ] All public classes/methods have Javadoc
- [ ] Unit tests for domain + application; IT for adapters (Testcontainers)
- [ ] ArchUnit hexagonal rules green
- [ ] `@RolesAllowed("ADMIN")` on every moderation bean/page
- [ ] Design tokens only
- [ ] English only
- [ ] No secrets in code

**Definition of Done for the Epic:**
- Customers can submit a review (rating 1-5 + optional text) on a product
- Verified-purchase flag is correctly calculated
- Public product-detail page shows average rating, star distribution and approved reviews
- Admin has a moderation queue (list + approve/reject)
- All ArchUnit, unit and integration tests pass
- `mvn clean package -pl web -am` succeeds
- No new dependencies added

Start with Step 0 of the implementation sequence.
```
