# Admin Dashboard Module — Implementation Sequencing Appendix

**Companion to:** `admin-dashboard-module-spec.md` (what to build) and `admin-dashboard-backlog.md` (why, sliced into stories). This document is the **execution order** — read it before writing any code. It enables the implementing agent to work step-by-step without stopping to ask "what do I do first?"

**Rule for the implementing agent:** work through the steps in order. Do not start step N+1 until step N's "Done when" checklist is fully satisfied. If a step's prerequisites aren't met, stop and report rather than improvising.

---

## Step 0 — Environment & Configuration Setup

1. **Confirm Admin Dashboard module structure will reside in existing modules:**
   - [ ] `user-domain/` — NOT creating new domain; admin dashboard is data aggregation, not domain-driven
   - [ ] `admin-domain/` — NEW module for application layer (ports, services, DTOs)
   - [ ] `catalog-adapters/` — EXTEND with admin query adapters
   - [ ] `catalog-web/` — EXTEND with admin JSF beans and pages

2. **Confirm database schema prepared:**
   - [ ] Flyway migration V6 (indexes for admin queries) ready to apply
   - [ ] Indexes on: ORDER_ENTITY(status, created_at), USER_ACCOUNT(created_at), PRODUCT_ENTITY(created_at), PAYMENT_TRANSACTION(order_id), USER_AUDIT_LOG(user_id, created_at)

3. **Confirm chart library choice:**
   - [ ] PrimeFaces Charts selected (already included if PrimeFaces in project)
   - [ ] OR Apache ECharts (if preferred)

4. **Confirm caching strategy:**
   - [ ] Guava Cache for in-memory caching (5-min TTL for dashboard metrics)
   - [ ] Added to `catalog-adapters/pom.xml`:
     ```xml
     <dependency>
       <groupId>com.google.guava</groupId>
       <artifactId>guava</artifactId>
       <version>31.1-jre</version>
     </dependency>
     ```

5. **Confirm reporting libraries:**
   - [ ] Flying Saucer (HTML → PDF) OR iText
   - [ ] Apache Commons CSV (CSV export)

6. **Verify admin-domain module structure:**
   ```bash
   mkdir -p admin-domain/src/main/java/.../admin/domain/exception
   mkdir -p admin-domain/src/main/java/.../admin/application/port/in
   mkdir -p admin-domain/src/main/java/.../admin/application/port/out
   mkdir -p admin-domain/src/main/java/.../admin/application/service
   mkdir -p admin-domain/src/main/java/.../admin/application/dto
   mkdir -p admin-domain/src/test/java/.../admin
   ```

**Done when:**
- [ ] All dependencies resolve (`mvn dependency:resolve`)
- [ ] admin-domain module structure ready
- [ ] Flyway V6 migration script prepared
- [ ] Chart and reporting libraries chosen and added to pom.xml

---

## Step 1 — Create Admin Exception Classes

All in `admin-domain/src/main/java/.../admin/domain/exception/`:

1. **`UnauthorizedAdminAccessException.java`**
```java
public class UnauthorizedAdminAccessException extends RuntimeException {
    public UnauthorizedAdminAccessException(String message) {
        super(message);
    }
    
    public UnauthorizedAdminAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

2. **`OrderNotFoundException.java`**
```java
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String message) {
        super(message);
    }
}
```

3. **`ProductNotFoundException.java`**
```java
public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String message) {
        super(message);
    }
}
```

4. **`RefundProcessingException.java`**
```java
public class RefundProcessingException extends RuntimeException {
    public RefundProcessingException(String message) {
        super(message);
    }
    
    public RefundProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

5. **`ReportGenerationException.java`**
```java
public class ReportGenerationException extends RuntimeException {
    public ReportGenerationException(String message) {
        super(message);
    }
    
    public ReportGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

**Done when:**
- [ ] All 5 exception classes compile
- [ ] No framework imports
- [ ] All have proper constructors (message, message+cause)

---

## Step 2 — Create All DTOs (27+ DTOs)

All in `admin-domain/src/main/java/.../admin/application/dto/`:

**Dashboard DTOs:**

1. **`DashboardMetricsDTO.java`** — immutable record
```java
public record DashboardMetricsDTO(
    BigDecimal revenueToday,
    BigDecimal revenueThisMonth,
    BigDecimal revenueYTD,
    Integer ordersToday,
    Integer ordersThisMonth,
    Integer ordersYTD,
    Integer newCustomersToday,
    Integer newCustomersThisMonth,
    Integer newCustomersYTD,
    Map<OrderStatus, Integer> ordersByStatus,
    BigDecimal averageOrderValue,
    Double conversionRate,
    Double cartAbandonmentRate,
    Instant calculatedAt,
    Instant nextRefreshAt
) {}
```

**Order DTOs:**

2. **`OrderAdminDTO.java`** — immutable record
```java
public record OrderAdminDTO(
    Long id,
    String orderNumber,
    Long customerId,
    String customerName,
    String customerEmail,
    BigDecimal total,
    OrderStatus status,
    LocalDateTime createdAt,
    LocalDateTime lastUpdatedAt,
    Integer itemCount,
    PaymentStatus paymentStatus
) {}
```

3. **`OrderDetailsDTO.java`** — final class (complex)
```java
public final class OrderDetailsDTO {
    public final Long id;
    public final String orderNumber;
    // ... (30+ fields)
    
    public OrderDetailsDTO(...) { ... }
    
    // Getters, equals, hashCode
}
```

4. **`OrderLineDTO.java`**
5. **`PaymentTransactionDTO.java`**
6. **`TimelineEventDTO.java`**
7. **`ShippingAddressDTO.java`**
8. **`BillingAddressDTO.java`**

**Product DTOs:**

9. **`ProductAdminDTO.java`**
```java
public record ProductAdminDTO(
    Long id,
    String sku,
    String name,
    String description,
    List<String> categoryNames,
    BigDecimal price,
    BigDecimal costPrice,
    Integer quantityInStock,
    Integer unitsSold,
    BigDecimal totalRevenue,
    Double averageRating,
    Integer reviewCount,
    Boolean isActive,
    LocalDateTime createdAt,
    LocalDateTime lastUpdatedAt,
    List<String> imageUrls
) {}
```

**Customer DTOs:**

10. **`CustomerAdminDTO.java`**
11. **`AddressDTO.java`**

**Refund DTOs:**

12. **`RefundRequestDTO.java`**

**Report DTOs:**

13. **`RevenueReportDTO.java`**
14. **`ProductReportDTO.java`**
15. **`ProductPerformanceDTO.java`**
16. **`CustomerReportDTO.java`**

**Audit DTOs:**

17. **`AuditLogEntryDTO.java`**

**Admin User DTOs:**

18. **`AdminUserDTO.java`**

**Search Criteria DTOs:**

19. **`OrderAdminSearchCriteria.java`** — final class with builder
20. **`ProductAdminSearchCriteria.java`**
21. **`UserAdminSearchCriteria.java`**
22. **`RefundSearchCriteria.java`**
23. **`AuditLogSearchCriteria.java`**

**Generic DTOs (Reused):**

24. **`PageResult<T>.java`** — (reuse from User Account module if available, else create)
```java
public record PageResult<T>(
    List<T> items,
    Integer totalElements,
    Integer page,
    Integer pageSize
) {
    public Integer totalPages() {
        return (totalElements + pageSize - 1) / pageSize;
    }
}
```

**Done when:**
- [ ] All DTOs compile
- [ ] All are immutable (records or final classes with no setters)
- [ ] All have comprehensive Javadoc
- [ ] All search criteria have builder pattern or constructor validation
- [ ] PageResult<T> is generic and reusable

---

## Step 3 — Create All Outbound Port Interfaces

All in `admin-domain/src/main/java/.../admin/application/port/out/`:

1. **`DashboardMetricsPort.java`** — complex aggregation queries
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

6. **`RefundPort.java`** — refund queries + updates
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

8. **`PaymentGatewayPort.java`** — (reused from Order module, but confirm it has processRefund method)
```java
public interface PaymentGatewayPort {
    String processRefund(String originalTransactionId, BigDecimal amount) throws PaymentFailedException;
}
```

**Done when:**
- [ ] All ports compile
- [ ] All have comprehensive Javadoc explaining what queries/operations they perform
- [ ] Return types match DTOs created in Step 2

---

## Step 4 — Create All Inbound Use Case Interfaces

All in `admin-domain/src/main/java/.../admin/application/port/in/`:

Create 23 use case interfaces (one per story S4–S25, excluding S26–S27 which are enforcement, not use cases):

1. **`GetDashboardMetricsUseCase.java`**
```java
public interface GetDashboardMetricsUseCase {
    DashboardMetricsDTO getDashboardMetrics();
}
```

2. **`ListOrdersUseCase.java`**
```java
public interface ListOrdersUseCase {
    PageResult<OrderAdminDTO> listOrders(int page, int pageSize, OrderAdminSearchCriteria criteria);
}
```

3. **`GetOrderDetailsUseCase.java`**
4. **`UpdateOrderStatusUseCase.java`**
5. **`ListProductsForAdminUseCase.java`**
6. **`CreateProductUseCase.java`**
7. **`UpdateProductUseCase.java`**
8. **`DeactivateProductUseCase.java`**
9. **`ListUsersUseCase.java`** (admin view, not User Account's ListUsersUseCase)
10. **`GetUserDetailsUseCase.java`**
11. **`BlockUserUseCase.java`**
12. **`UnblockUserUseCase.java`**
13. **`ListRefundRequestsUseCase.java`**
14. **`ApproveRefundUseCase.java`**
15. **`RejectRefundUseCase.java`**
16. **`GenerateRevenueReportUseCase.java`**
17. **`GenerateProductReportUseCase.java`**
18. **`GenerateCustomerReportUseCase.java`**
19. **`ViewAuditLogUseCase.java`**
20. **`CreateAdminUserUseCase.java`**
21. **`ListAdminUsersUseCase.java`**

**Done when:**
- [ ] All 21 use case interfaces compile
- [ ] Each has 1–3 methods only (single responsibility)
- [ ] All have comprehensive Javadoc

---

## Step 5 — Create Admin Application Service

In `admin-domain/src/main/java/.../admin/application/service/`:

1. **`AdminDashboardService.java`** — implements all 21 use cases

```java
@ApplicationScoped
@Transactional
public class AdminDashboardService implements
    GetDashboardMetricsUseCase,
    ListOrdersUseCase,
    GetOrderDetailsUseCase,
    UpdateOrderStatusUseCase,
    ListProductsForAdminUseCase,
    CreateProductUseCase,
    UpdateProductUseCase,
    DeactivateProductUseCase,
    ListUsersUseCase,
    GetUserDetailsUseCase,
    BlockUserUseCase,
    UnblockUserUseCase,
    ListRefundRequestsUseCase,
    ApproveRefundUseCase,
    RejectRefundUseCase,
    GenerateRevenueReportUseCase,
    GenerateProductReportUseCase,
    GenerateCustomerReportUseCase,
    ViewAuditLogUseCase,
    CreateAdminUserUseCase,
    ListAdminUsersUseCase {
    
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
    private AuditLogPort auditLog;  // to log admin actions
    
    private void checkAdminRole() {
        if (!currentUser.hasRole(Role.ADMIN)) {
            throw new UnauthorizedAdminAccessException(
                "User " + currentUser.email() + " is not admin"
            );
        }
    }
    
    @Override
    public DashboardMetricsDTO getDashboardMetrics() {
        checkAdminRole();
        return metrics.getDashboardMetrics();
    }
    
    @Override
    public PageResult<OrderAdminDTO> listOrders(int page, int pageSize, OrderAdminSearchCriteria criteria) {
        checkAdminRole();
        PageResult<OrderAdminDTO> result = orderQuery.findOrders(criteria, page, pageSize);
        auditLog.logEvent(currentUser.id(), "ORDER_LIST_VIEWED", 
            "Admin viewed orders page " + page);
        return result;
    }
    
    // ... implement all 21 use cases
}
```

**Structure:** Each use case method:
1. Calls `checkAdminRole()`
2. Delegates to port
3. Logs action via `AuditLogPort`
4. Returns result or throws exception

**Done when:**
- [ ] AdminDashboardService compiles
- [ ] Implements all 21 use case interfaces
- [ ] Every method starts with `checkAdminRole()`
- [ ] Every method logs audit event
- [ ] All ports injected (@Inject)

---

## Step 6 — Create Query Adapters (Core of Admin Dashboard)

All in `catalog-adapters/src/main/java/.../admin/adapter/persistence/`:

### 6.1 **`DashboardMetricsAdapter.java`** — Complex aggregation queries

```java
@ApplicationScoped
public class DashboardMetricsAdapter implements DashboardMetricsPort {
    
    @Inject
    private EntityManager em;
    
    @Inject
    @ConfigProperty(name = "dashboard.metrics.cache.minutes", defaultValue = "5")
    private Integer cacheMinutes;
    
    private final Cache<String, DashboardMetricsDTO> cache = 
        CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();
    
    @Override
    public DashboardMetricsDTO getDashboardMetrics() {
        // Check cache first
        DashboardMetricsDTO cached = cache.getIfPresent("dashboard-metrics");
        if (cached != null && cached.nextRefreshAt().isAfter(Instant.now())) {
            return cached;
        }
        
        // Execute complex queries
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate yearStart = LocalDate.of(today.getYear(), 1, 1);
        Instant todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant monthStart_inst = monthStart.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant yearStart_inst = yearStart.atStartOfDay(ZoneId.systemDefault()).toInstant();
        
        // Query 1: Revenue today
        BigDecimal revenueToday = em.createQuery(
            "SELECT COALESCE(SUM(o.total), 0) FROM OrderJpaEntity o " +
            "WHERE o.createdAt >= :todayStart AND o.status NOT IN (OrderStatus.CANCELLED)",
            BigDecimal.class
        )
            .setParameter("todayStart", todayStart)
            .getSingleResult();
        
        // Query 2: Revenue this month
        BigDecimal revenueThisMonth = em.createQuery(
            "SELECT COALESCE(SUM(o.total), 0) FROM OrderJpaEntity o " +
            "WHERE o.createdAt >= :monthStart AND o.status NOT IN (OrderStatus.CANCELLED)",
            BigDecimal.class
        )
            .setParameter("monthStart", monthStart_inst)
            .getSingleResult();
        
        // Query 3: Revenue YTD
        BigDecimal revenueYTD = em.createQuery(
            "SELECT COALESCE(SUM(o.total), 0) FROM OrderJpaEntity o " +
            "WHERE o.createdAt >= :yearStart AND o.status NOT IN (OrderStatus.CANCELLED)",
            BigDecimal.class
        )
            .setParameter("yearStart", yearStart_inst)
            .getSingleResult();
        
        // Query 4: Orders today
        Integer ordersToday = em.createQuery(
            "SELECT COUNT(o) FROM OrderJpaEntity o " +
            "WHERE o.createdAt >= :todayStart",
            Integer.class
        )
            .setParameter("todayStart", todayStart)
            .getSingleResult()
            .intValue();
        
        // Query 5: Orders this month
        Integer ordersThisMonth = em.createQuery(
            "SELECT COUNT(o) FROM OrderJpaEntity o " +
            "WHERE o.createdAt >= :monthStart",
            Integer.class
        )
            .setParameter("monthStart", monthStart_inst)
            .getSingleResult()
            .intValue();
        
        // Query 6: Orders YTD
        Integer ordersYTD = em.createQuery(
            "SELECT COUNT(o) FROM OrderJpaEntity o " +
            "WHERE o.createdAt >= :yearStart",
            Integer.class
        )
            .setParameter("yearStart", yearStart_inst)
            .getSingleResult()
            .intValue();
        
        // Query 7: New customers today
        Integer newCustomersToday = em.createQuery(
            "SELECT COUNT(u) FROM UserJpaEntity u " +
            "WHERE u.createdAt >= :todayStart AND u.status = UserStatus.ACTIVE",
            Integer.class
        )
            .setParameter("todayStart", todayStart)
            .getSingleResult()
            .intValue();
        
        // Query 8: Orders by status (today)
        List<Object[]> statusCounts = em.createQuery(
            "SELECT o.status, COUNT(o) FROM OrderJpaEntity o " +
            "WHERE o.createdAt >= :todayStart " +
            "GROUP BY o.status",
            Object[].class
        )
            .setParameter("todayStart", todayStart)
            .getResultList();
        
        Map<OrderStatus, Integer> ordersByStatus = new EnumMap<>(OrderStatus.class);
        for (Object[] row : statusCounts) {
            ordersByStatus.put((OrderStatus) row[0], ((Number) row[1]).intValue());
        }
        
        // Query 9: Average order value
        BigDecimal averageOrderValue = ordersToday > 0 
            ? revenueToday.divide(new BigDecimal(ordersToday), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        
        // Query 10: Conversion rate (mock)
        Double conversionRate = 2.5;  // placeholder
        
        // Query 11: Cart abandonment rate (mock)
        Double cartAbandonmentRate = 15.2;  // placeholder
        
        // Build DTO and cache
        DashboardMetricsDTO metrics = new DashboardMetricsDTO(
            revenueToday,
            revenueThisMonth,
            revenueYTD,
            ordersToday,
            ordersThisMonth,
            ordersYTD,
            newCustomersToday,
            // ... remaining fields
            ordersByStatus,
            averageOrderValue,
            conversionRate,
            cartAbandonmentRate,
            Instant.now(),
            Instant.now().plus(Duration.ofMinutes(cacheMinutes))
        );
        
        cache.put("dashboard-metrics", metrics);
        return metrics;
    }
}
```

### 6.2 **`OrderQueryAdapter.java`** — Complex order queries with joins

```java
@ApplicationScoped
public class OrderQueryAdapter implements OrderQueryPort {
    
    @Inject
    private EntityManager em;
    
    @Override
    public PageResult<OrderAdminDTO> findOrders(
        OrderAdminSearchCriteria criteria,
        int page,
        int pageSize) {
        
        // Build dynamic JPQL query
        StringBuilder jpql = new StringBuilder(
            "SELECT new admin.dto.OrderAdminDTO(" +
            "o.id, o.orderNumber, u.id, u.firstName, u.lastName, u.email, " +
            "o.total, o.status, o.createdAt, o.updatedAt, " +
            "(SELECT COUNT(ol) FROM OrderLineJpaEntity ol WHERE ol.order.id = o.id), " +
            "o.paymentStatus) " +
            "FROM OrderJpaEntity o " +
            "JOIN FETCH o.customer u " +
            "WHERE 1=1"
        );
        
        Map<String, Object> params = new HashMap<>();
        
        if (criteria.status() != null) {
            jpql.append(" AND o.status = :status");
            params.put("status", criteria.status());
        }
        if (criteria.customerId() != null) {
            jpql.append(" AND u.id = :customerId");
            params.put("customerId", criteria.customerId());
        }
        if (criteria.fromDate() != null) {
            jpql.append(" AND o.createdAt >= :fromDate");
            params.put("fromDate", criteria.fromDate().atStartOfDay(ZoneId.systemDefault()).toInstant());
        }
        if (criteria.toDate() != null) {
            jpql.append(" AND o.createdAt < :toDate");
            params.put("toDate", criteria.toDate().atStartOfDay(ZoneId.systemDefault()).toInstant());
        }
        if (criteria.searchQuery() != null && !criteria.searchQuery().isBlank()) {
            jpql.append(" AND (LOWER(o.orderNumber) LIKE :searchQuery OR " +
                       "LOWER(u.firstName) LIKE :searchQuery OR " +
                       "LOWER(u.lastName) LIKE :searchQuery OR " +
                       "LOWER(u.email) LIKE :searchQuery)");
            params.put("searchQuery", "%" + criteria.searchQuery().toLowerCase() + "%");
        }
        
        jpql.append(" ORDER BY o.createdAt DESC");
        
        // Create query
        TypedQuery<OrderAdminDTO> query = em.createQuery(jpql.toString(), OrderAdminDTO.class);
        params.forEach(query::setParameter);
        
        // Count total
        String countJpql = jpql.toString()
            .replaceAll("SELECT new admin\\.dto\\.OrderAdminDTO\\(.*?\\)", "SELECT COUNT(o)")
            .replaceAll("ORDER BY.*", "");
        
        Long totalCount = em.createQuery(countJpql, Long.class).getSingleResult();
        
        // Paginate
        List<OrderAdminDTO> results = query
            .setFirstResult((page - 1) * pageSize)
            .setMaxResults(pageSize)
            .getResultList();
        
        return new PageResult<>(results, totalCount.intValue(), page, pageSize);
    }
    
    @Override
    public Optional<OrderDetailsDTO> findOrderDetails(Long orderId) {
        // Complex query with all joins and aggregations
        OrderDetailsDTO details = em.createQuery(
            "SELECT new admin.dto.OrderDetailsDTO(...) " +
            "FROM OrderJpaEntity o " +
            "JOIN FETCH o.lines " +
            "JOIN FETCH o.customer u " +
            "LEFT JOIN FETCH PaymentTransactionJpaEntity p ON p.order.id = o.id " +
            "WHERE o.id = :orderId",
            OrderDetailsDTO.class
        )
            .setParameter("orderId", orderId)
            .getSingleResult();
        
        return Optional.of(details);
    }
}
```

### 6.3 **`ProductQueryAdapter.java`** — Product queries with sales stats

Similar pattern to OrderQueryAdapter, but queries PRODUCT_ENTITY with sales statistics:
- SUM(order_line.quantity) as unitsSold
- SUM(order_line.unit_price * order_line.quantity) as totalRevenue
- AVG(product_review.rating) as averageRating

### 6.4 **`UserQueryAdapter.java`** — Customer queries with LTV

Similar pattern, queries USER_ACCOUNT with:
- SUM(order.total) as lifetimeValue
- COUNT(order) as totalOrders
- AVG(order.total) as averageOrderValue

### 6.5 **`AuditLogQueryAdapter.java`** — Audit log queries

Simple adapter querying USER_AUDIT_LOG table with filters (user_id, event_type, date_range).

**Done when:**
- [ ] All 5 adapters compile
- [ ] Each implements corresponding port interface
- [ ] Complex queries tested with Testcontainers (1000+ test records)
- [ ] Query performance verified (< 1 second with indexes)
- [ ] No N+1 queries (JOIN FETCH verified)
- [ ] DashboardMetricsAdapter caching works (5-min TTL)

---

## Step 7 — Create Reporting Adapter

In `catalog-adapters/src/main/java/.../admin/adapter/reporting/`:

### 7.1 **`ReportGeneratorAdapter.java`** — PDF & CSV export

```java
@ApplicationScoped
public class ReportGeneratorAdapter implements ReportPort {
    
    @Inject
    private PDFExportService pdfService;
    
    @Inject
    private CSVExportService csvService;
    
    @Override
    public byte[] generatePDF(
        Object reportData,
        String templateName,
        ReportOptions options) throws ReportGenerationException {
        
        try {
            // Load Freemarker template
            Template template = pdfService.loadTemplate(templateName + ".ftl");
            
            // Render template with data
            StringWriter output = new StringWriter();
            template.process(reportData, output);
            String html = output.toString();
            
            // Convert HTML → PDF using Flying Saucer or iText
            ByteArrayOutputStream pdfStream = new ByteArrayOutputStream();
            pdfService.renderHTMLtoPDF(html, pdfStream, options);
            
            return pdfStream.toByteArray();
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate PDF: " + e.getMessage(), e);
        }
    }
    
    @Override
    public byte[] generateCSV(
        List<?> data,
        String templateName) throws ReportGenerationException {
        
        try {
            StringWriter output = new StringWriter();
            CSVPrinter printer = new CSVPrinter(output, CSVFormat.DEFAULT);
            
            // Write header and rows
            csvService.writeToCsv(data, printer);
            printer.flush();
            
            return output.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate CSV: " + e.getMessage(), e);
        }
    }
}
```

### 7.2 **`PDFExportService.java`** — PDF generation helper

### 7.3 **`CSVExportService.java`** — CSV generation helper

### 7.4 **Freemarker templates** (in `src/main/resources/admin/reports/`)

- `revenue-report.ftl` — Revenue report HTML template
- `product-report.ftl` — Product performance template
- `customer-report.ftl` — Customer insights template

**Done when:**
- [ ] ReportGeneratorAdapter compiles and implements ReportPort
- [ ] PDF generation tested (validates layout, embeds images)
- [ ] CSV generation tested (Excel-compatible formatting)
- [ ] Templates created and render correctly

---

## Step 8 — Create Admin JSF Beans

All in `catalog-web/servlet/src/main/java/.../admin/web/jsf/beans/`:

### 8.1 **`DashboardBean.java`** — Home dashboard

```java
@Named("dashboardBean")
@ViewScoped
@RolesAllowed("ADMIN")
public class DashboardBean implements Serializable {
    
    @Inject
    private GetDashboardMetricsUseCase getDashboardMetrics;
    
    private DashboardMetricsDTO metrics;
    private ChartModel revenueChart;
    private ChartModel ordersByStatusChart;
    
    @PostConstruct
    void loadDashboard() {
        try {
            metrics = getDashboardMetrics.getDashboardMetrics();
            initCharts();
        } catch (UnauthorizedAdminAccessException e) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, "Access Denied", e.getMessage()));
        }
    }
    
    private void initCharts() {
        // Initialize revenue line chart
        // Initialize orders by status pie/bar chart
    }
    
    public DashboardMetricsDTO getMetrics() { return metrics; }
    public ChartModel getRevenueChart() { return revenueChart; }
    public ChartModel getOrdersByStatusChart() { return ordersByStatusChart; }
    
    public String getRevenueFormatted() {
        return metrics != null 
            ? "R$ " + new DecimalFormat("#,##0.00").format(metrics.revenueToday())
            : "R$ 0.00";
    }
}
```

### 8.2 **`OrderManagementBean.java`** — Order list and detail

```java
@Named("orderManagementBean")
@ViewScoped
@RolesAllowed("ADMIN")
public class OrderManagementBean implements Serializable {
    
    @Inject
    private ListOrdersUseCase listOrders;
    
    @Inject
    private GetOrderDetailsUseCase getOrderDetails;
    
    @Inject
    private UpdateOrderStatusUseCase updateOrderStatus;
    
    private PageResult<OrderAdminDTO> orders;
    private OrderDetailsDTO selectedOrderDetail;
    private OrderAdminSearchCriteria searchCriteria;
    private int currentPage = 1;
    private int pageSize = 20;
    
    @PostConstruct
    void loadOrders() {
        search();
    }
    
    public void search() {
        try {
            orders = listOrders.listOrders(currentPage, pageSize, searchCriteria);
        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error", e.getMessage()));
        }
    }
    
    public void viewOrderDetail(Long orderId) {
        try {
            selectedOrderDetail = getOrderDetails.getOrderDetails(orderId);
        } catch (OrderNotFoundException e) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, "Not Found", e.getMessage()));
        }
    }
    
    public void updateStatus(OrderStatus newStatus) {
        try {
            updateOrderStatus.updateOrderStatus(selectedOrderDetail.id(), newStatus);
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO, "Success", "Order status updated"));
            viewOrderDetail(selectedOrderDetail.id());  // Refresh
        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error", e.getMessage()));
        }
    }
    
    // Getters and search field methods
}
```

### 8.3–8.8 Create remaining beans

- **`ProductManagementBean.java`** — product list, create, edit, deactivate
- **`CustomerManagementBean.java`** — customer list and detail
- **`RefundManagementBean.java`** — refund list, approve, reject
- **`ReportGenerationBean.java`** — revenue, product, customer reports + export
- **`AuditLogBean.java`** — audit log viewer
- **`AdminUserManagementBean.java`** — admin user CRUD (@RolesAllowed("SUPER_ADMIN"))

**Done when:**
- [ ] All 8 beans compile
- [ ] All decorated with @Named, @ViewScoped or @RequestScoped
- [ ] All with @RolesAllowed("ADMIN") or @RolesAllowed("SUPER_ADMIN")
- [ ] All use cases injected (@Inject)
- [ ] @PostConstruct loads initial data
- [ ] Action methods (search, save, update, delete) implemented
- [ ] Error handling via FacesMessage
- [ ] All beans tested (integration tests)

---

## Step 9 — Create Admin JSF Pages

All in `catalog-web/servlet/src/main/webapp/admin/`:

### 9.1 **`dashboard.xhtml`** — Home dashboard

```xhtml
<ui:composition xmlns="http://www.w3.org/1999/xhtml" xmlns:h="http://xmlns.jcp.org/jsf/html" xmlns:p="http://primefaces.org/ui" template="/WEB-INF/templates/admin-layout.xhtml">

<ui:define name="content">
    <h1>Admin Dashboard</h1>
    
    <!-- KPI Cards -->
    <div class="metrics-row">
        <div class="metric-card">
            <h3>Revenue Today</h3>
            <p class="metric-value">#{dashboardBean.revenueFormatted}</p>
            <p class="metric-trend">+5% from yesterday</p>
        </div>
        
        <div class="metric-card">
            <h3>Orders Today</h3>
            <p class="metric-value">#{dashboardBean.metrics.ordersToday}</p>
        </div>
        
        <div class="metric-card">
            <h3>New Customers</h3>
            <p class="metric-value">#{dashboardBean.metrics.newCustomersToday}</p>
        </div>
        
        <div class="metric-card">
            <h3>Avg Order Value</h3>
            <p class="metric-value">#{dashboardBean.metrics.averageOrderValue}</p>
        </div>
    </div>
    
    <!-- Charts -->
    <div class="charts-row">
        <div class="chart-container">
            <p:chart type="line" model="#{dashboardBean.revenueChart}" style="width: 100%; height: 300px;" />
        </div>
        
        <div class="chart-container">
            <p:chart type="bar" model="#{dashboardBean.ordersByStatusChart}" style="width: 100%; height: 300px;" />
        </div>
    </div>
    
    <!-- Quick Actions -->
    <div class="quick-actions">
        <h2>Quick Actions</h2>
        <h:link outcome="orders/list" value="View All Orders" />
        <h:link outcome="refunds/list" value="Pending Refunds" />
        <h:link outcome="products/list" value="Manage Products" />
        <h:link outcome="customers/list" value="View Customers" />
    </div>
</ui:define>

</ui:composition>
```

### 9.2 **`orders/list.xhtml`** — Order list with filters

### 9.3 **`orders/detail.xhtml`** — Order detail view

### 9.4–9.7 Create remaining pages

- **`products/list.xhtml`** — product list
- **`products/create.xhtml`** — create product form
- **`products/edit.xhtml`** — edit product form
- **`customers/list.xhtml`** — customer list
- **`customers/detail.xhtml`** — customer profile
- **`refunds/list.xhtml`** — refund requests list
- **`refunds/detail.xhtml`** — refund detail + approve/reject
- **`reports/index.xhtml`** — report selection
- **`reports/revenue.xhtml`** — revenue report + export
- **`reports/products.xhtml`** — product performance report
- **`reports/customers.xhtml`** — customer insights report
- **`audit-log/view.xhtml`** — audit log viewer
- **`settings/admin-users.xhtml`** — admin user management

Also create **admin layout template** (`/WEB-INF/templates/admin-layout.xhtml`):
```xhtml
<h:html>
  <h:head>
    <title>Admin Dashboard</title>
    <link rel="stylesheet" href="#{resource['css/admin.css']}" />
  </h:head>
  <h:body>
    <div class="admin-container">
      <nav class="admin-sidebar">
        <ul>
          <li><h:link outcome="dashboard" value="Dashboard" /></li>
          <li><h:link outcome="orders/list" value="Orders" /></li>
          <li><h:link outcome="products/list" value="Products" /></li>
          <li><h:link outcome="customers/list" value="Customers" /></li>
          <li><h:link outcome="refunds/list" value="Refunds" /></li>
          <li><h:link outcome="reports/index" value="Reports" /></li>
          <li><h:link outcome="audit-log/view" value="Audit Log" /></li>
          <li><h:link outcome="settings/admin-users" value="Admin Users" rendered="#{currentUser.hasRole('SUPER_ADMIN')}" /></li>
        </ul>
      </nav>
      
      <div class="admin-content">
        <ui:insert name="content" />
      </div>
    </div>
  </h:body>
</h:html>
```

**Done when:**
- [ ] All pages compile (no JSF/EL errors)
- [ ] All pages accessible only to ADMIN users (@RolesAllowed enforced)
- [ ] All forms bind to beans (h:inputText, h:selectOneMenu, h:dataTable)
- [ ] All buttons call bean action methods
- [ ] Charts render correctly (PrimeFaces <p:chart>)
- [ ] Pagination works (p:dataTable paginator)
- [ ] Modals/dialogs functional (p:dialog)
- [ ] Forms validated (client-side + server-side)
- [ ] Error messages display (h:messages)

---

## Step 10 — Create Flyway Migration V6 (Indexes)

In `catalog-adapters/src/main/resources/db/migration/`:

**`V6__admin_dashboard_indexes.sql`**

```sql
-- Order queries optimization
CREATE INDEX idx_order_status_created ON ORDER_ENTITY(status, created_at DESC);
CREATE INDEX idx_order_customer_created ON ORDER_ENTITY(customer_id, created_at DESC);
CREATE INDEX idx_order_created ON ORDER_ENTITY(created_at DESC);

-- Product queries optimization
CREATE INDEX idx_product_created ON PRODUCT_ENTITY(created_at DESC);
CREATE INDEX idx_product_active_created ON PRODUCT_ENTITY(is_active, created_at DESC);

-- User queries optimization
CREATE INDEX idx_user_created ON USER_ACCOUNT(created_at DESC);
CREATE INDEX idx_user_last_login ON USER_ACCOUNT(last_login_at DESC);

-- Payment transaction queries
CREATE INDEX idx_payment_order ON PAYMENT_TRANSACTION(order_id);
CREATE INDEX idx_payment_type_status ON PAYMENT_TRANSACTION(type, status);

-- Audit log queries
CREATE INDEX idx_audit_admin_user ON USER_AUDIT_LOG(user_id, created_at DESC);
CREATE INDEX idx_audit_event_type ON USER_AUDIT_LOG(event_type);
```

**Done when:**
- [ ] Migration file created and named V6__...
- [ ] Flyway picks it up automatically on app start
- [ ] Indexes created in DB (verify via DB admin tool)
- [ ] Query performance improved (< 500ms for paginated queries)

---

## Step 11 — Create CSS for Admin Dashboard

In `catalog-web/servlet/src/main/resources/css/`:

**`admin.css`**

```css
/* Admin Dashboard Styling */
.admin-container {
  display: flex;
  min-height: 100vh;
}

.admin-sidebar {
  width: 250px;
  background: #2c3e50;
  color: white;
  padding: 20px;
}

.admin-sidebar ul {
  list-style: none;
  padding: 0;
}

.admin-sidebar li {
  margin: 10px 0;
}

.admin-sidebar a {
  color: white;
  text-decoration: none;
  display: block;
  padding: 10px;
  border-radius: 4px;
}

.admin-sidebar a:hover {
  background: #34495e;
}

.admin-content {
  flex: 1;
  padding: 30px;
  background: #ecf0f1;
}

/* KPI Cards */
.metrics-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 20px;
  margin-bottom: 30px;
}

.metric-card {
  background: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.metric-card h3 {
  margin: 0 0 10px 0;
  color: #7f8c8d;
  font-size: 14px;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.metric-value {
  font-size: 32px;
  font-weight: bold;
  color: #2c3e50;
  margin: 10px 0;
}

.metric-trend {
  font-size: 12px;
  color: #27ae60;
  margin: 5px 0 0 0;
}

/* Tables */
.ui-datatable {
  background: white;
}

.ui-datatable-header {
  background: #f5f5f5;
  border-bottom: 2px solid #bdc3c7;
}

/* Status Badges */
.status-pending { color: #f39c12; font-weight: bold; }
.status-confirmed { color: #2980b9; font-weight: bold; }
.status-processing { color: #8e44ad; font-weight: bold; }
.status-shipped { color: #16a085; font-weight: bold; }
.status-delivered { color: #27ae60; font-weight: bold; }
.status-cancelled { color: #e74c3c; font-weight: bold; }
.status-rejected { color: #c0392b; font-weight: bold; }

/* Forms */
.ui-inputtext, .ui-selectonemenu {
  width: 100%;
  padding: 10px;
  border: 1px solid #bdc3c7;
  border-radius: 4px;
}

.ui-button {
  background: #3498db;
  color: white;
  padding: 10px 20px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.ui-button:hover {
  background: #2980b9;
}
```

**Done when:**
- [ ] admin.css created
- [ ] Admin pages styled consistently
- [ ] Responsive design (mobile-friendly)
- [ ] Colors match branding
- [ ] Tables, cards, forms styled

---

## Step 12 — Create Tests

In `catalog-adapters/src/test/java/.../admin/`:

### 12.1 **`DashboardMetricsAdapterTest.java`** (Testcontainers)

```java
@Testcontainers
public class DashboardMetricsAdapterTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:14"));
    
    private DashboardMetricsAdapter adapter;
    private EntityManager em;
    
    @BeforeEach
    void setup() {
        adapter = new DashboardMetricsAdapter();
        em = getEntityManagerForTestContainer(postgres);
    }
    
    @Test
    void testGetDashboardMetrics_withOrdersToday() {
        // Setup: Insert test data (orders created today)
        OrderJpaEntity order = new OrderJpaEntity();
        order.setTotal(new BigDecimal("100.00"));
        order.setStatus(OrderStatus.CONFIRMED);
        order.setCreatedAt(Instant.now());
        em.persist(order);
        
        // Test
        DashboardMetricsDTO metrics = adapter.getDashboardMetrics();
        
        // Assert
        assertNotNull(metrics);
        assertEquals(new BigDecimal("100.00"), metrics.revenueToday());
        assertEquals(1, metrics.ordersToday());
    }
    
    @Test
    void testCaching_refreshesAfter5Minutes() {
        // Get metrics
        DashboardMetricsDTO metrics1 = adapter.getDashboardMetrics();
        
        // Cache should return same instance
        DashboardMetricsDTO metrics2 = adapter.getDashboardMetrics();
        
        assertEquals(metrics1.calculatedAt(), metrics2.calculatedAt());
    }
    
    // ... more test cases
}
```

### 12.2 **`OrderQueryAdapterTest.java`** (Testcontainers)

Test findOrders with various filters, pagination, sorting.

### 12.3 **`AdminDashboardServiceTest.java`** (Unit tests)

Test each use case with mocked ports.

**Done when:**
- [ ] All adapter tests pass (Testcontainers with real DB)
- [ ] All service tests pass (unit tests with mocked ports)
- [ ] Query performance verified (< 1 second)
- [ ] Caching tested (TTL, invalidation)

---

## Step 13 — Create ArchUnit Tests

In `catalog-adapters/src/test/java/.../admin/`:

**`AdminHexagonalArchitectureTest.java`**

```java
public class AdminHexagonalArchitectureTest {
    
    private static final String ADMIN_DOMAIN = "admin.domain";
    private static final String ADMIN_APPLICATION = "admin.application";
    private static final String ADMIN_ADAPTER = "admin.adapter";
    
    @Test
    void adminApplicationHasNoAdapterImports() {
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage(ADMIN_APPLICATION)
            .should()
            .accessClassesThat()
            .resideInAPackage(ADMIN_ADAPTER)
            .check(new ClassFileImporter().importPackages(ADMIN_APPLICATION, ADMIN_ADAPTER));
    }
    
    @Test
    void adminAdaptersDoNotImportEachOther() {
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage(ADMIN_ADAPTER + ".persistence")
            .should()
            .accessClassesThat()
            .resideInAPackage(ADMIN_ADAPTER + ".reporting")
            .check(new ClassFileImporter().importPackages(ADMIN_ADAPTER));
    }
    
    @Test
    void adminUseCasesResideInPortInPackage() {
        ArchRuleDefinition.classes()
            .that()
            .implement(Serializable.class)  // all use cases are interfaces
            .and()
            .haveSimpleNameEndingWith("UseCase")
            .should()
            .resideInAPackage(ADMIN_APPLICATION + ".port.in")
            .check(new ClassFileImporter().importPackages(ADMIN_APPLICATION));
    }
    
    @Test
    void adminPortsResideInPortPackages() {
        ArchRuleDefinition.classes()
            .that()
            .haveSimpleNameEndingWith("Port")
            .should()
            .resideInAPackage(ADMIN_APPLICATION + ".port..")
            .check(new ClassFileImporter().importPackages(ADMIN_APPLICATION));
    }
    
    // ... more tests
}
```

**Done when:**
- [ ] All ArchUnit tests pass
- [ ] Boundaries enforced automatically
- [ ] Tests integrated into CI/CD pipeline

---

## Full Sequence Completion Checklist

- [ ] Step 0 — Environment & config
- [ ] Step 1 — Exception classes
- [ ] Step 2 — DTOs (27+)
- [ ] Step 3 — Outbound ports (8)
- [ ] Step 4 — Inbound use cases (21)
- [ ] Step 5 — Application service
- [ ] Step 6 — Query adapters (5)
- [ ] Step 7 — Reporting adapter
- [ ] Step 8 — JSF beans (8)
- [ ] Step 9 — JSF pages (13+)
- [ ] Step 10 — Flyway V6 migration
- [ ] Step 11 — CSS styling
- [ ] Step 12 — Tests (adapters + service + ArchUnit)
- [ ] Step 13 — ArchUnit enforcement

Only after every box above is checked is the Admin Dashboard module truly complete.

---

## Final Build Verification

```bash
# Clean build (must succeed)
mvn clean install

# Run all Admin Dashboard tests
mvn test -Dtest=*Admin*,*Dashboard*,*Order*Admin*,*Product*Admin*,*Customer*Admin*

# Run ArchUnit tests
mvn test -Dtest=AdminHexagonalArchitectureTest

# Build deployable EAR
mvn clean install -pl ear

# Deploy to Open Liberty and smoke test:
# 1. Navigate to http://localhost:9080/onlineshop/admin/dashboard
# 2. See dashboard with KPI cards and charts
# 3. Click "View All Orders" → see order list, filters work
# 4. Click order → see detail, update status, approve refund
# 5. Click "Manage Products" → see product list, create/edit products
# 6. Click "View Customers" → see customer list with LTV
# 7. Click "Reports" → generate revenue report, export as PDF
# 8. Check audit log → see all admin actions logged
```
