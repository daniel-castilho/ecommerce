### 1. `tasks/ai-software-engineer-prompt-wishlist.md`

```markdown
# AI Software Engineer Prompt — Wishlist Module

**Status:** Not implemented — green-field epic.
**Target module:** `wishlist` (or `product-wishlist`)
**Package:** `com.loja.wishlist`

You implement a complete Wishlist module for this Jakarta EE 11 + Faces hexagonal monolith.

---

## Sources of truth (read in order)

1. `AGENTS.md`
2. `docs/lessons.md` · `docs/java-jakarta-ee-coding-standards.md` · `docs/design-system.md`
3. `tasks/wishlist-module-spec.md` — what to build
4. `tasks/wishlist-backlog.md` — stories
5. `tasks/wishlist-implementation-sequence.md` — build order
6. Reference modules: `product-reviews`, `product-catalog`, `user-account`

---

## Goal

Authenticated customers can **save / remove products** on a personal wishlist, list them, and jump to product detail or checkout.
No social sharing, no multiple named lists, no price-drop alerts in MVP.

---

## Non-negotiable rules

- Hexagonal layout identical to `product-reviews`
- Domain free of frameworks; DTOs in `application/dto` (never nested in ports)
- Cross-module access **only** via ports (`product-catalog` product lookup; session from `user-account`)
- English only; design-system tokens only
- No new Maven dependency without human approval
- ArchUnit + unit tests + adapter ITs (Testcontainers)
- New JPA entity → register in `web` `persistence.xml`
- Do not push unless the human asks

---

## Definition of Done (epic)

- [ ] Add / remove wishlist item (authenticated)
- [ ] List “my wishlist” with product snapshot (name, price, image URL if available, slug)
- [ ] Idempotent add (unique user+product); remove is safe if missing
- [ ] UI: button on product-detail (and optionally catalog card); page `wishlist.xhtml`
- [ ] ArchUnit green; module tests green; `mvn clean package -pl web -am` succeeds

Start at **Step 0** of `wishlist-implementation-sequence.md`. If scope is unclear, **stop and ask**.
```
