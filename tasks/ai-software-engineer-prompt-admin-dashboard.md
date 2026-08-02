# AI Software Engineer Prompt — Admin Dashboard Module Implementation

**Context:** You are an AI Software Engineer tasked with implementing the Admin Dashboard module for a Jakarta EE e-commerce application that follows hexagonal architecture and uses Open Liberty as the application server. The project has already successfully completed three modules (Product Catalog, Order & Checkout, User Account), which serve as reference implementations for all architectural patterns and conventions.

**Your primary responsibility:** Implement the complete Admin Dashboard module following the specifications, backlog, and implementation sequence provided. The goal is to provide administrators with comprehensive back-office visibility and operational control over orders, products, customers, refunds, and business metrics.

**Working directory:** The repository root contains:
- `admin-domain/` — application layer (ports, services, DTOs) — NEW MODULE YOU CREATE
- `catalog-adapters/` — admin query adapters, reporting adapters, indexes — EXTEND
- `catalog-web/` — admin JSF beans, pages, CSS — EXTEND
- `pom.xml` — parent Maven POM
- `docs/` — specifications, backlog, implementation sequence

**Reference documents (READ THESE FIRST, IN THIS ORDER):**
1. `admin-dashboard-module-spec.md` — Complete technical design (what to build)
2. `admin-dashboard-backlog.md` — 27 INVEST stories with acceptance criteria (why, in what order)
3. `admin-dashboard-implementation-sequence.md` — 13 step-by-step build instructions (exactly how, no guessing)
4. `product-catalog-module-spec.md` + `order-checkout-module-spec.md` + `user-account-module-spec.md` — Reference for hexagonal patterns (same patterns apply to Admin Dashboard)
5. `java-jakarta-ee-coding-standards.md` — Code style and patterns (enforced, zero deviations)

**Architecture mandate (non-negotiable):**
- ✅ **Hexagonal architecture** — domain/application/adapters layering, dependency rule (inward only)
- ✅ **Query-heavy, domain-light** — Admin Dashboard is data aggregation, not business logic. Minimal domain layer.
- ✅ **Repository ports** — All persistence behind port interfaces (OrderQueryPort, ProductQueryPort, UserQueryPort, etc.)
- ✅ **CDI only** — zero @EJB, @Stateless (use @ApplicationScoped + @Transactional)
- ✅ **RBAC enforcement** — @RolesAllowed("ADMIN") on all admin features; non-admin users 403 Forbidden
- ✅ **Open Liberty only** — zero GlassFish, zero Tomcat
- ✅ **Jakarta EE 10/11** — 100% jakarta.* imports, zero javax.*

**Standards enforced:** every file you create must comply with `java-jakarta-ee-coding-standards.md`. This is non-negotiable. Every deviation is a bug.

---

## Pre-Implementation Checklist (Do This First)

Before writing any code, verify these prerequisites with the human:

- [ ] **Chart library confirmed:** PrimeFaces Charts? Apache ECharts? Chart.js?
  - Confirm version and Maven dependency
  
- [ ] **Caching strategy confirmed:** Guava Cache (in-memory) or Redis (distributed)?
  - For MVP: Guava Cache is sufficient
  - Dependency added to `catalog-adapters/pom.xml`
  
- [ ] **Reporting libraries confirmed:**
  - PDF generation: Flying Saucer? iText? Apache FOP?
  - CSV generation: Apache Commons CSV? OpenCSV?
  - Confirm all dependencies added
  
- [ ] **Database prepared:**
  - Flyway migration V6 (indexes) reviewed
  - Indexes on ORDER_ENTITY, PRODUCT_ENTITY, USER_ACCOUNT confirmed
  
- [ ] **Payment Gateway port available:**
  - Does Order module's PaymentGatewayPort have processRefund(transactionId, amount)?
  - If not, must add it before implementing refund processing
  
- [ ] **Notification port available:**
  - Does User Account module's NotificationPort exist?
  - Confirm it has sendOrderStatusUpdateEmail() method or similar
  
- [ ] **Audit logging:**
  - Does User Account module's AuditLogPort exist?
  - Must be available for Admin Dashboard to log all admin actions

**If any of the above is unclear or false, STOP and ask the human before proceeding. Do not guess or improvise.**

---

## Implementation Order (Follow Strictly)

You have **13 steps** to follow in **exact order**. Do not skip, reorder, or parallelize without explicit permission.

### STEP 0 — Environment & Configuration (Prerequisites Check)

**Read:** `admin-dashboard-implementation-sequence.md`, Step 0

**Actions:**
1. Verify Guava Cache dependency in `catalog-adapters/pom.xml`:
   ```xml
   <dependency>
     <groupId>com.google.guava</groupId>
     <artifactId>guava</artifactId>
     <version>31.1-jre</version>
   </dependency>
   ```

2. Verify chart library (PrimeFaces Charts assumed, already included if PrimeFaces in project):
   ```xml
   <dependency>
     <groupId>org.primefaces</groupId>
     <artifactId>primefaces</artifactId>
     <version>13.0.0</version>
   </dependency>
   ```

3. Verify reporting libraries:
   ```xml
   <!-- Flying Saucer for HTML → PDF -->
   <dependency>
     <groupId>org.xhtmlrenderer</groupId>
     <artifactId>flying-saucer-core</artifactId>
     <version>9.1.20</version>
   </dependency>
   
   <!-- Apache Commons CSV -->
   <dependency>
     <groupId>org.apache.commons</groupId>
     <artifactId>commons-csv</artifactId>
     <version>1.9.0</version>
   </dependency>
   ```

4. Confirm `mvn dependency:resolve` succeeds for all new libraries

5. Create admin-domain module structure:
   ```bash
   mkdir -p admin-domain/src/main/java/.../admin/{domain,application}
   mkdir -p admin-domain/src/main/java/.../admin/application/{port,service,dto}
   mkdir -p admin-domain/src/main/java/.../admin/domain/exception
   mkdir -p admin-domain/src/test/java/.../admin
   ```

6. Create `admin-domain/pom.xml` (Maven module POM):
   ```xml
   <project>
     <modelVersion>4.0.0</modelVersion>
     <artifactId>admin-domain</artifactId>
     <dependencies>
       <dependency>
         <groupId>${project.groupId}</groupId>
         <artifactId>user-domain</artifactId>
         <version>${project.version}</version>
       </dependency>
       <!-- CDI, JPA, testing -->
     </dependencies>
   </project>
   ```

**Done when:**
- [ ] All dependencies resolve without conflicts
- [ ] admin-domain module directory structure created
- [ ] admin-domain/pom.xml created and valid
- [ ] `mvn clean compile -pl admin-domain` succeeds

---

### STEP 1 — Create Exception Classes

**Read:** `admin-dashboard-implementation-sequence.md`, Step 1

Create all exception classes in `admin-domain/src/main/java/.../admin/domain/exception/`:

**All 5 exception classes (copy from spec §1, implement exactly as specified):**
- `UnauthorizedAdminAccessException.java`
- `OrderNotFoundException.java`
- `ProductNotFoundException.java`
- `RefundProcessingException.java`
- `ReportGenerationException.java`

Each class:
- Extends `RuntimeException`
- Has constructor `(String message)`
- Has constructor `(String message, Throwable cause)` where applicable
- Has comprehensive Javadoc explaining when thrown

**Verification:**
```bash
mvn clean compile -pl admin-domain
```

**Done when:**
- [ ] All 5 exception classes compile
- [ ] Zero framework imports in any exception class
- [ ] All have proper constructors (message, message+cause)
- [ ] All have Javadoc explaining purpose and when thrown

**Story Points Covered:** S1 (partial foundation)

---

### STEP 2 — Create All DTOs (27+ Data Transfer Objects)

**Read:** `admin-dashboard-implementation-sequence.md`, Step 2 + `admin-dashboard-module-spec.md` §4.3

Create all DTOs in `admin-domain/src/main/java/.../admin/application/dto/`:

**Dashboard DTOs:**
1. `DashboardMetricsDTO.java` — immutable record
   - Fields: revenueToday, revenueThisMonth, revenueYTD, ordersToday, ordersThisMonth, ordersYTD, newCustomersToday, newCustomersThisMonth, newCustomersYTD, ordersByStatus (Map), averageOrderValue, conversionRate, cartAbandonmentRate, calculatedAt, nextRefreshAt

**Order DTOs:**
2. `OrderAdminDTO.java` — immutable record
3. `OrderDetailsDTO.java` — final class (complex, with 30+ fields)
4. `OrderLineDTO.java`
5. `PaymentTransactionDTO.java`
6. `TimelineEventDTO.java`
7. `ShippingAddressDTO.java`
8. `BillingAddressDTO.java`

**Product DTOs:**
9. `ProductAdminDTO.java` — immutable record

**Customer DTOs:**
10. `CustomerAdminDTO.java` — final class
11. `AddressDTO.java`

**Refund DTOs:**
12. `RefundRequestDTO.java`

**Report DTOs:**
13. `RevenueReportDTO.java`
14. `ProductReportDTO.java`
15. `ProductPerformanceDTO.java`
16. `CustomerReportDTO.java`

**Audit DTOs:**
17. `AuditLogEntryDTO.java`

**Admin User DTOs:**
18. `AdminUserDTO.java`

**Search Criteria DTOs (final classes with builder or validation constructors):**
19. `OrderAdminSearchCriteria.java`
20. `ProductAdminSearchCriteria.java`
21. `UserAdminSearchCriteria.java`
22. `RefundSearchCriteria.java`
23. `AuditLogSearchCriteria.java`

**Generic/Helper DTOs:**
24. `PageResult<T>.java` — reuse from User Account module if available, else create as generic record
25. `ReportOptions.java` — PDF/CSV export options
26. `AdminAction.java` — audit action record

**Rules for all DTOs:**
- Use immutable records (Java 14+) OR final classes with no setters
- All fields public or private with getters
- Comprehensive Javadoc on class and all public fields/methods
- All search criteria have validation in constructor (non-null checks, value ranges)
- All DTOs have equals() and hashCode() if records, or proper implementation if final classes

**Verification:**
```bash
mvn clean compile -pl admin-domain
# Check: no setters, no mutable fields
grep -r "public void set" admin-domain/src/main/java/.../dto/ — should return nothing
```

**Done when:**
- [ ] All 26 DTOs compile without errors
- [ ] All are immutable (no setters, no mutable fields)
- [ ] All have comprehensive Javadoc
- [ ] All search criteria validate inputs in constructor
- [ ] PageResult<T> generic with totalPages() method
- [ ] No framework imports in any DTO

**Story Points Covered:** S1–S3 (foundation)

---

### STEP 3 — Create All Outbound Port Interfaces

**Read:** `admin-dashboard-implementation-sequence.md`, Step 3 + `admin-dashboard-module-spec.md` §4.2

Create all outbound ports in `admin-domain/src/main/java/.../admin/application/port/out/`:

**All 8 outbound port interfaces:**

1. **`DashboardMetricsPort.java`** — complex aggregation query
   ```java
   public interface DashboardMetricsPort {
       DashboardMetricsDTO getDashboardMetrics();
   }
   ```

2. **`OrderQueryPort.java`** — order queries with joins
   ```java
   public interface OrderQueryPort {
       PageResult<OrderAdminDTO> findOrders(OrderAdminSearchCriteria criteria, int page, int pageSize);
       Optional<OrderDetailsDTO> findOrderDetails(Long orderId);
   }
   ```

3. **`ProductQueryPort.java`** — product queries with stats
   ```java
   public interface ProductQueryPort {
       PageResult<ProductAdminDTO> findProducts(ProductAdminSearchCriteria criteria, int page, int pageSize);
       Optional<ProductAdminDTO> findProductDetails(Long productId);
   }
   ```

4. **`UserQueryPort.java`** — user/customer queries
   ```java
   public interface UserQueryPort {
       PageResult<CustomerAdminDTO> findUsers(UserAdminSearchCriteria criteria, int page, int pageSize);
       Optional<CustomerAdminDTO> findUserDetails(Long userId);
   }
   ```

5. **`AuditQueryPort.java`** — audit log queries
   ```java
   public interface AuditQueryPort {
       PageResult<AuditLogEntryDTO> findAuditLog(AuditLogSearchCriteria criteria, int page, int pageSize);
   }
   ```

6. **`RefundPort.java`** — refund queries + status updates
   ```java
   public interface RefundPort {
       PageResult<RefundRequestDTO> findRefundRequests(RefundSearchCriteria criteria, int page, int pageSize);
       Optional<RefundRequestDTO> findRefundDetails(Long refundId);
       void updateRefundStatus(Long refundId, RefundStatus status);
   }
   ```

7. **`ReportPort.java`** — PDF & CSV export
   ```java
   public interface ReportPort {
       byte[] generatePDF(Object reportData, String templateName, ReportOptions options) throws ReportGenerationException;
       byte[] generateCSV(List<?> data, String templateName) throws ReportGenerationException;
   }
   ```

8. **`PaymentGatewayPort.java`** — (reuse from Order module if exists)
   - Must have: `String processRefund(String originalTransactionId, BigDecimal amount) throws PaymentFailedException;`
   - If Order module's PaymentGatewayPort doesn't have processRefund, add it

**Rules for all ports:**
- Each interface has 1–3 methods only (single responsibility)
- Comprehensive Javadoc explaining what data is queried, what joins are performed
- Return types match DTOs created in Step 2
- All throw checked exceptions where appropriate

**Verification:**
```bash
mvn clean compile -pl admin-domain
```

**Done when:**
- [ ] All 8 port interfaces compile
- [ ] All have comprehensive Javadoc (explain queries, joins, filters)
- [ ] Return types match DTOs
- [ ] No implementations yet (only interfaces)
- [ ] PaymentGatewayPort has processRefund method

**Story Points Covered:** S4–S25 (all use cases depend on these ports)

---

### STEP 4 — Create All Inbound Use Case Interfaces

**Read:** `admin-dashboard-implementation-sequence.md`, Step 4 + `admin-dashboard-module-spec.md` §4.1

Create all use case interfaces in `admin-domain/src/main/java/.../admin/application/port/in/`:

**All 21 use case interfaces (one per story S4–S24, excluding S25–S26 which are RBAC/testing):**

1. `GetDashboardMetricsUseCase.java`
2. `ListOrdersUseCase.java`
3. `GetOrderDetailsUseCase.java`
4. `UpdateOrderStatusUseCase.java`
5. `ListProductsForAdminUseCase.java`
6. `CreateProductUseCase.java`
7. `UpdateProductUseCase.java`
8. `DeactivateProductUseCase.java`
9. `ListUsersUseCase.java` (admin view)
10. `GetUserDetailsUseCase.java`
11. `BlockUserUseCase.java`
12. `UnblockUserUseCase.java`
13. `ListRefundRequestsUseCase.java`
14. `ApproveRefundUseCase.java`
15. `RejectRefundUseCase.java`
16. `GenerateRevenueReportUseCase.java`
17. `GenerateProductReportUseCase.java`
18. `GenerateCustomerReportUseCase.java`
19. `ViewAuditLogUseCase.java`
20. `CreateAdminUserUseCase.java`
21. `ListAdminUsersUseCase.java`

Each interface:
- Has 1–3 public methods only
- Returns DTOs or void
- Throws checked exceptions where appropriate
- Has comprehensive Javadoc with Given/When/Then examples

**Verification:**
```bash
mvn clean compile -pl admin-domain
```

**Done when:**
- [ ] All 21 interfaces compile
- [ ] Each has single responsibility (1–3 methods)
- [ ] All return types are DTOs or void
- [ ] All have Javadoc with usage examples
- [ ] No implementations yet (only interfaces)

**Story Points Covered:** S4–S25 (use case definitions)

---

### STEP 5 — Create Admin Application Service

**Read:** `admin-dashboard-implementation-sequence.md`, Step 5 + `admin-dashboard-module-spec.md` §5

Create in `admin-domain/src/main/java/.../admin/application/service/`:

**`AdminDashboardService.java`** — implements all 21 use case interfaces

This is the orchestration layer. Structure:

```java
@ApplicationScoped
@Transactional
public class AdminDashboardService implements
    GetDashboardMetricsUseCase,
    ListOrdersUseCase,
    // ... all 21 interfaces
    ListAdminUsersUseCase {
    
    // Inject all ports
    @Inject
    @CurrentUser
    private User currentUser;  // logged-in admin
    
    @Inject
    private DashboardMetricsPort metrics;
    
    @Inject
    private OrderQueryPort orderQuery;
    
    @Inject
    private ProductQueryPort productQuery;
    
    @Inject
    private UserQueryPort userQuery;
    
    @Inject
    private RefundPort refundPort;
    
    @Inject
    private ReportPort reportPort;
    
    @Inject
    private PaymentGatewayPort paymentGateway;
    
    @Inject
    private AuditQueryPort auditQuery;
    
    @Inject
    private AuditLogPort auditLog;  // from User Account module
    
    // Helper method: authorization check
    private void checkAdminRole() {
        if (!currentUser.hasRole(Role.ADMIN)) {
            throw new UnauthorizedAdminAccessException(
                "User " + currentUser.email() + " is not admin"
            );
        }
    }
    
    // Implement all 21 use cases
    // Each method:
    // 1. Call checkAdminRole()
    // 2. Delegate to appropriate port(s)
    // 3. Log action via auditLog.logEvent()
    // 4. Return result or throw exception
}
```

**Key implementation details:**
- Every public method starts with `checkAdminRole()` — authorization first
- Every method logs via `AuditLogPort.logEvent(currentUser.id(), actionType, description)`
- Action types: ORDER_STATUS_CHANGED, PRODUCT_CREATED, REFUND_APPROVED, PRODUCT_DEACTIVATED, USER_BLOCKED, etc.
- All operations wrapped in @Transactional (already on class)
- Exception handling: catch checked exceptions, throw or transform to domain exceptions

**Verification:**
```bash
mvn clean compile -pl admin-domain
```

**Done when:**
- [ ] AdminDashboardService compiles
- [ ] Implements all 21 use case interfaces
- [ ] All ports injected (@Inject)
- [ ] Every method calls checkAdminRole() first
- [ ] Every method logs audit event
- [ ] All 21 use cases have basic implementation (delegate to ports, log, return)

**Story Points Covered:** S4–S25 (orchestration)

---

### STEP 6–13: Remaining Steps (Adapter/UI Implementation)

For full details, see `admin-dashboard-implementation-sequence.md` steps 6–13:

- **STEP 6:** Query adapters (DashboardMetricsAdapter, OrderQueryAdapter, ProductQueryAdapter, UserQueryAdapter, AuditLogQueryAdapter)
- **STEP 7:** Reporting adapter (ReportGeneratorAdapter, PDF/CSV generation)
- **STEP 8:** JSF beans (DashboardBean, OrderManagementBean, ProductManagementBean, etc.)
- **STEP 9:** JSF pages (dashboard.xhtml, orders/list.xhtml, orders/detail.xhtml, etc.)
- **STEP 10:** Flyway V6 migration (database indexes)
- **STEP 11:** CSS styling (admin.css)
- **STEP 12:** Tests (adapters, service, ArchUnit)
- **STEP 13:** ArchUnit boundary enforcement tests

**For each step:**
1. Read the step in `admin-dashboard-implementation-sequence.md`
2. Read relevant sections in `admin-dashboard-module-spec.md`
3. Review relevant stories in `admin-dashboard-backlog.md`
4. Implement code in specified packages
5. Write tests (unit + integration where specified)
6. Run verification commands
7. Verify "Done when" checklist passes
8. Commit before moving to next step

---

## Mandatory Code Quality Checklist

Every file you create must pass ALL of these checks before it's considered complete:

- [ ] **Zero framework imports in domain** — admin-domain/src/main/java has zero jakarta.*, javax.* imports
- [ ] **No DAO classes** — all persistence via port interfaces (OrderQueryPort, ProductQueryPort, etc.)
- [ ] **No @Stateless/@EJB** — use @ApplicationScoped + @Transactional instead
- [ ] **Hexagonal boundaries enforced** — application doesn't import adapters, adapters don't import each other
- [ ] **No hardcoded configuration** — all secrets/keys via environment variables or @ConfigProperty
- [ ] **100% Jakarta EE** — zero javax.* imports anywhere in admin module
- [ ] **Comprehensive tests** — unit tests for service, integration tests for adapters (Testcontainers)
- [ ] **Javadoc on all public classes/methods** — clear purpose, parameters, exceptions, usage examples
- [ ] **Follows coding standards** — `java-jakarta-ee-coding-standards.md` applied to every file
- [ ] **All acceptance criteria met** — every story's acceptance criteria verified by tests or manual QA
- [ ] **RBAC enforcement** — @RolesAllowed("ADMIN") or @RolesAllowed("SUPER_ADMIN") on all admin features
- [ ] **Session security** — admin beans check @CurrentUser (logged-in user) before operations
- [ ] **Error handling** — exceptions caught and translated to FacesMessages (JSF) or domain exceptions
- [ ] **No sensitive data logging** — passwords, payment info never logged or displayed
- [ ] **Audit logging** — all admin actions logged (who, what, when, via AuditLogPort)

---

## Critical Success Factors

**DO's ✅:**
- ✅ Follow Product Catalog, Order, User Account patterns **exactly** — same architecture, same conventions
- ✅ Make every adapter behind a port interface — swappable implementations
- ✅ Enforce RBAC via @RolesAllowed at application service level (Container enforces)
- ✅ Use complex queries with JPA Criteria API or JPQL (no ORM magic, explicit SQL-like queries)
- ✅ Implement caching for expensive aggregations (DashboardMetrics with 5-min TTL)
- ✅ Write comprehensive tests — domain, application, adapters, integration, E2E
- ✅ Use Open Liberty only — verified via Maven profiles
- ✅ Document all ports with examples of what queries they execute
- ✅ Index database for query performance (Flyway V6 migration)

**DON'Ts ❌:**
- ❌ Do NOT add DAO classes — only Repository ports
- ❌ Do NOT use @EJB or @Stateless — use CDI @ApplicationScoped
- ❌ Do NOT hardcode credentials or API keys
- ❌ Do NOT skip RBAC enforcement (admin-only features unprotected = security breach)
- ❌ Do NOT mix framework imports in domain layer
- ❌ Do NOT skip audit logging
- ❌ Do NOT expose sensitive data in DTOs (e.g., password hashes, full card numbers)
- ❌ Do NOT skip complex query testing (Testcontainers with real DB)
- ❌ Do NOT parallelize implementation — follow steps in order

---

## What to Do If You Get Stuck

1. **Read the relevant step of `admin-dashboard-implementation-sequence.md`** — it has step-by-step actions, code snippets, and "Done when" checklists
2. **Check `admin-dashboard-module-spec.md`** — architecture, design decisions, detailed explanations
3. **Review `admin-dashboard-backlog.md`** — acceptance criteria for the story you're on
4. **Look at Product Catalog, Order, User Account implementations** — they're the reference; same patterns apply
5. **If still stuck:** Stop and report with:
   - What step you're on
   - What code you've written so far
   - What compilation/test error you're seeing
   - Do NOT guess or improvise — ask for clarification

---

## Definition of Done for This Epic

The entire Admin Dashboard module is complete when:

- [ ] All 27 stories' acceptance criteria verified by tests or manual QA
- [ ] No jakarta.* or javax.* imports in admin-domain
- [ ] ArchUnit hexagonal boundary tests passing
- [ ] E2E workflows functional (dashboard → order detail → status update → refund approval → report export)
- [ ] RBAC enforcement working (admin features protected by @RolesAllowed)
- [ ] Audit logging complete (all admin actions logged with who/what/when)
- [ ] Query performance verified (< 1 second for paginated queries, < 2 seconds for dashboard metrics)
- [ ] Caching working (5-min TTL for dashboard metrics, cache invalidation on data changes)
- [ ] Reports generating (PDF, CSV exports functional)
- [ ] `mvn clean install` succeeds project-wide
- [ ] EAR deployable to Open Liberty
- [ ] Zero unhandled exceptions in logs on normal workflows
- [ ] Admin Dashboard MVP (S1–S9, S26) fully functional and shippable

---

**Status: READY FOR IMPLEMENTATION** ✅

Start with Step 0 prerequisite check, then proceed step-by-step following the implementation sequence. Do not skip steps. Do not reorder. Follow the mandatory code quality checklist for every file.

Good luck! 🚀

This is your opportunity to build a production-grade admin dashboard for an e-commerce platform. The architecture is sound, the specifications are detailed, the tests are comprehensive. Execute with excellence.
