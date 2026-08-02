# Admin Dashboard Module — Comprehensive Technical Specification

**Audience:** this document is written for an AI coding agent that will implement the Admin Dashboard module directly in the `java-ee-online-shop` repository. It assumes complete familiarity with the established patterns from Product Catalog, Order & Checkout, and User Account modules, which serve as reference implementations for hexagonal architecture, CDI, JPA, JSF, and Jakarta EE 10/11 patterns.

**Status:** specification for implementation. All architectural decisions are locked (non-negotiable). Section 14 lists assumptions the implementer should flag if they prove incorrect.

---

## 0. Architecture: Hexagonal with Admin-Only Access & Complex Aggregations

The Admin Dashboard module differs from previous modules in that it is **purely administrative** — it consumes and aggregates data from Order, Product, and User modules without creating new business entities. It sits at the **application-wide reporting and analytics tier**, providing operational visibility to administrators.

**Key architectural principle:** Admin Dashboard is **data-read-heavy, write-infrequent**. It queries Orders, Products, Users, and Payment Transactions but does not own them. It translates complex queries into simple, read-only data structures (DTOs) for the UI. No new JPA entities. No domain model (minimal). No new business logic (mostly orchestration and presentation logic).

**Access Control (RBAC):** Every Admin Dashboard page/bean/use-case is protected by `@RolesAllowed("ADMIN")`. Non-admin users cannot access any admin feature, even with direct URL manipulation (Container enforces at servlet level).

**Dependency Architecture:**

```
User Account Module
    ↓
Product Catalog Module
    ↓
Order & Checkout Module
    ↓
Admin Dashboard Module (aggregates all 3 above, adds no dependencies on itself to others)
```

**Data Flow:**

```
Dashboard Bean (@ViewScoped, @RolesAllowed("ADMIN"))
    ↓
Admin Use Cases (GetDashboardMetricsUseCase, ListOrdersUseCase, etc.)
    ↓
Application Service (AdminDashboardService)
    ↓
Read-Only Ports (DashboardMetricsPort, OrderQueryPort, ProductQueryPort, UserQueryPort, ReportPort)
    ↓
Adapter Layer:
    ├─ DashboardMetricsAdapter — complex aggregation queries
    ├─ OrderQueryAdapter — ORDER queries with joins
    ├─ ProductQueryAdapter — PRODUCT queries with stats
    ├─ UserQueryAdapter — USER queries with customer insights
    ├─ ReportGeneratorAdapter — PDF/CSV export
    └─ RefundProcessorAdapter — refund orchestration
```

---

## 1. Purpose & Scope

Enable administrators to manage e-commerce operations via a comprehensive back-office dashboard. Provide real-time visibility into orders, products, customers, and revenue. Support order lifecycle management (status updates, refunds, shipping label generation). Generate reports and exports for business analysis.

**In scope:**
- Dashboard home with key metrics (revenue, orders, new customers, status summary)
- Order management (list, filter, view details, update status, process refunds)
- Product management (create, edit, deactivate, view sales stats)
- User/Customer management (list, view details, block/unblock, view order history)
- Refund processing (list requests, approve/reject, process payment reversal)
- Reports & analytics (revenue by period, top products, top customers, channel breakdown)
- Audit log viewer (see who did what when)
- Admin user roles & permissions (create/edit/delete admin accounts)

**Out of scope (Phase 2):**
- Inventory management (assumed managed by Catalog module separately)
- Marketing campaigns & promotions (future module)
- Customer communication (emails, SMS — notifications separate module)
- Advanced analytics (ML-powered recommendations, cohort analysis)
- Multi-store/multi-currency (single store MVP)
- Webhook management (third-party integrations)
- API key management (admin API separate module)

---

## 2. Module / Package Layout

```
admin-domain/src/main/java/.../admin/domain/
├── exception/
│   ├── UnauthorizedAdminAccessException.java
│   ├── OrderNotFoundException.java
│   ├── ProductNotFoundException.java
│   ├── RefundProcessingException.java
│   └── ReportGenerationException.java

admin-domain/src/main/java/.../admin/application/
├── port/in/
│   ├── GetDashboardMetricsUseCase.java
│   ├── ListOrdersUseCase.java
│   ├── GetOrderDetailsUseCase.java
│   ├── UpdateOrderStatusUseCase.java
│   ├── ProcessRefundUseCase.java
│   ├── ListProductsForAdminUseCase.java
│   ├── CreateProductUseCase.java
│   ├── UpdateProductUseCase.java
│   ├── DeactivateProductUseCase.java
│   ├── ListUsersUseCase.java
│   ├── GetUserDetailsUseCase.java
│   ├── BlockUserUseCase.java
│   ├── UnblockUserUseCase.java
│   ├── ListRefundRequestsUseCase.java
│   ├── ApproveRefundUseCase.java
│   ├── RejectRefundUseCase.java
│   ├── GenerateRevenueReportUseCase.java
│   ├── GenerateProductReportUseCase.java
│   ├── GenerateCustomerReportUseCase.java
│   ├── ViewAuditLogUseCase.java
│   ├── CreateAdminUserUseCase.java
│   ├── ListAdminUsersUseCase.java
│   └── ManageAdminRolesUseCase.java
├── port/out/
│   ├── DashboardMetricsPort.java
│   ├── OrderQueryPort.java
│   ├── ProductQueryPort.java
│   ├── UserQueryPort.java
│   ├── RefundPort.java
│   ├── ReportPort.java
│   ├── AuditQueryPort.java
│   └── PaymentGatewayPort.java (for refund processing)
├── service/
│   ├── AdminDashboardService.java
│   ├── AdminOrderService.java
│   ├── AdminProductService.java
│   ├── AdminUserService.java
│   ├── AdminRefundService.java
│   └── AdminReportService.java
└── dto/
    ├── DashboardMetricsDTO.java
    ├── OrderAdminDTO.java
    ├── OrderDetailsDTO.java
    ├── ProductAdminDTO.java
    ├── CustomerAdminDTO.java
    ├── RefundRequestDTO.java
    ├── RevenueReportDTO.java
    ├── ProductReportDTO.java
    ├── AuditLogEntryDTO.java
    └── PageResult.java (reuse from User Account)

catalog-adapters/src/main/java/.../admin/adapter/
├── persistence/
│   ├── DashboardMetricsAdapter.java
│   ├── OrderQueryAdapter.java
│   ├── ProductQueryAdapter.java
│   ├── UserQueryAdapter.java
│   ├── AuditLogQueryAdapter.java
│   └── AdminQueryCache.java (caching metrics)
├── reporting/
│   ├── ReportGeneratorAdapter.java (PDF, CSV)
│   ├── PDFExportService.java
│   ├── CSVExportService.java
│   └── ReportTemplate.java (Freemarker templates)
└── payment/
    └── RefundProcessorAdapter.java (calls PaymentGatewayPort to reverse charge)

catalog-web/servlet/src/main/java/.../admin/web/jsf/beans/
├── DashboardBean.java                (@ViewScoped, @RolesAllowed("ADMIN"))
├── OrderManagementBean.java          (@ViewScoped, @RolesAllowed("ADMIN"))
├── ProductManagementBean.java        (@ViewScoped, @RolesAllowed("ADMIN"))
├── CustomerManagementBean.java       (@ViewScoped, @RolesAllowed("ADMIN"))
├── RefundManagementBean.java         (@ViewScoped, @RolesAllowed("ADMIN"))
├── ReportGenerationBean.java         (@ViewScoped, @RolesAllowed("ADMIN"))
├── AuditLogBean.java                 (@ViewScoped, @RolesAllowed("ADMIN"))
└── AdminUserManagementBean.java      (@ViewScoped, @RolesAllowed("ADMIN"))

catalog-web/servlet/src/main/webapp/
├── admin/
│   ├── dashboard.xhtml               (home, metrics cards, charts)
│   ├── orders/
│   │   ├── list.xhtml                (order list, filters, status chips)
│   │   ├── detail.xhtml              (order detail, timeline, payment, items)
│   │   ├── status-update.xhtml       (modal to change status)
│   │   └── refund.xhtml              (refund form)
│   ├── products/
│   │   ├── list.xhtml                (product list, search, filters)
│   │   ├── create.xhtml              (new product form)
│   │   ├── edit.xhtml                (edit product)
│   │   ├── detail.xhtml              (product view, sales stats)
│   │   └── bulk-upload.xhtml         (CSV bulk import)
│   ├── customers/
│   │   ├── list.xhtml                (customer list, filters)
│   │   ├── detail.xhtml              (customer profile, order history)
│   │   └── block-unblock.xhtml       (account actions)
│   ├── refunds/
│   │   ├── list.xhtml                (refund requests, status)
│   │   ├── detail.xhtml              (refund details, approval form)
│   │   └── history.xhtml             (refund history)
│   ├── reports/
│   │   ├── index.xhtml               (report selection page)
│   │   ├── revenue.xhtml             (revenue report, date range, chart)
│   │   ├── products.xhtml            (product performance report)
│   │   ├── customers.xhtml           (customer analysis report)
│   │   └── export.xhtml              (export format selection, download)
│   ├── audit-log/
│   │   └── view.xhtml                (audit log, date range filter)
│   └── settings/
│       └── admin-users.xhtml         (manage admin accounts)
```

---

## 3. Domain Layer (Minimal, Data-Aggregation Focused)

Unlike previous modules (User, Order, Catalog which have rich domain models), Admin Dashboard has **minimal domain logic**. It primarily aggregates and queries data from other modules. No new Aggregate Roots. No complex state machines.

### 3.1 Exception Classes (in `admin-domain/exception/`)

```java
public class UnauthorizedAdminAccessException extends RuntimeException {
    // Thrown when non-admin tries to access admin feature
}

public class OrderNotFoundException extends RuntimeException {
    // Thrown when order ID not found
}

public class ProductNotFoundException extends RuntimeException {
    // Thrown when product ID not found
}

public class RefundProcessingException extends RuntimeException {
    // Thrown when refund fails (payment gateway error)
}

public class ReportGenerationException extends RuntimeException {
    // Thrown when PDF/CSV generation fails
}
```

### 3.2 Value Objects (Data Containers)

Admin Dashboard primarily uses **DTOs instead of value objects** because it's read-only, presentation-focused data aggregation (not business logic).

However, a few light value objects for consistency:

**`DashboardMetrics.java`** — immutable container for dashboard KPIs

```java
public final class DashboardMetrics {
    private final BigDecimal revenueToday;
    private final BigDecimal revenueThisMonth;
    private final Integer ordersToday;
    private final Integer ordersThisMonth;
    private final Integer newCustomersToday;
    private final Integer newCustomersThisMonth;
    private final Map<String, Integer> ordersByStatus;  // PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
    private final BigDecimal averageOrderValue;
    private final Double conversionRate;  // % of cart → checkout
    private final Double cartAbandonmentRate;
    private final Instant calculatedAt;
    
    // Constructor validates no nulls, non-negative values
    public DashboardMetrics(...) { ... }
    
    // Getters (no setters — immutable)
}
```

**`AdminAction.java`** — represents an action taken by an admin (for audit)

```java
public final class AdminAction {
    private final Long adminUserId;
    private final String actionType;  // ORDER_STATUS_CHANGED, PRODUCT_CREATED, REFUND_APPROVED, etc.
    private final String entityType;  // ORDER, PRODUCT, REFUND, USER
    private final Long entityId;
    private final String description;
    private final Instant timestamp;
    
    // Constructors, getters (immutable)
}
```

### 3.3 No Aggregate Roots or State Machines

Admin Dashboard does **not** create new entities that manage state. It reads from existing modules. No `AdminOrder`, no `AdminProduct` domain objects. Instead, it loads `Order` from Order module, `Product` from Catalog module, and presents them via DTOs.

---

## 4. Application Ports & DTOs (The Core of Admin Dashboard)

Admin Dashboard is **port-heavy, domain-light**. It defines many read-only ports (queries) and uses DTOs to shape data for presentation.

### 4.1 Inbound Ports (Use Cases)

All in `admin-domain/application/port/in/`:

#### **Dashboard & Metrics**

```java
public interface GetDashboardMetricsUseCase {
    /**
     * Returns key metrics for dashboard home: revenue, orders, customers, status breakdown.
     * Cached for 5 minutes (refreshes via background job).
     */
    DashboardMetricsDTO getDashboardMetrics();
}
```

#### **Order Management Use Cases**

```java
public interface ListOrdersUseCase {
    /**
     * Lists all orders with filters: status, customer, date range.
     * Returns paginated results (20 per page default).
     */
    PageResult<OrderAdminDTO> listOrders(
        int page,
        int pageSize,
        OrderAdminSearchCriteria criteria
    );
}

public interface GetOrderDetailsUseCase {
    /**
     * Gets full order detail: items, shipping address, payment, timeline events.
     */
    OrderDetailsDTO getOrderDetails(Long orderId);
}

public interface UpdateOrderStatusUseCase {
    /**
     * Updates order status (PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED).
     * Validates status transition rules.
     * Sends notification to customer.
     * Logs admin action.
     */
    void updateOrderStatus(Long orderId, OrderStatus newStatus) throws OrderNotFoundException;
}
```

#### **Product Management Use Cases**

```java
public interface ListProductsForAdminUseCase {
    /**
     * Lists all products with admin view: sales count, revenue, active/inactive status.
     */
    PageResult<ProductAdminDTO> listProducts(
        int page,
        int pageSize,
        ProductAdminSearchCriteria criteria
    );
}

public interface CreateProductUseCase {
    /**
     * Creates new product (admin can set prices, categories, images).
     */
    ProductAdminDTO createProduct(CreateProductRequest request);
}

public interface UpdateProductUseCase {
    /**
     * Updates existing product (price, name, category, images).
     */
    ProductAdminDTO updateProduct(Long productId, UpdateProductRequest request);
}

public interface DeactivateProductUseCase {
    /**
     * Soft-deactivates product (hides from customer view, keeps data).
     */
    void deactivateProduct(Long productId);
}
```

#### **Customer Management Use Cases**

```java
public interface ListUsersUseCase {
    /**
     * Lists all customers with admin view: LTV (lifetime value), orders, last login.
     */
    PageResult<CustomerAdminDTO> listUsers(
        int page,
        int pageSize,
        UserAdminSearchCriteria criteria
    );
}

public interface GetUserDetailsUseCase {
    /**
     * Gets customer profile with order history, addresses, preferences.
     */
    CustomerAdminDTO getUserDetails(Long userId);
}

public interface BlockUserUseCase {
    /**
     * Blocks user account (prevents login, marks suspicious).
     */
    void blockUser(Long userId, String reason);
}

public interface UnblockUserUseCase {
    /**
     * Unblocks user account.
     */
    void unblockUser(Long userId);
}
```

#### **Refund Management Use Cases**

```java
public interface ListRefundRequestsUseCase {
    /**
     * Lists all refund requests with status: PENDING, APPROVED, REJECTED, PROCESSED.
     */
    PageResult<RefundRequestDTO> listRefundRequests(
        int page,
        int pageSize,
        RefundSearchCriteria criteria
    );
}

public interface ApproveRefundUseCase {
    /**
     * Approves a refund request.
     * Calls PaymentGateway to reverse charge.
     * Sends notification to customer.
     * Logs admin action.
     */
    void approveRefund(Long refundRequestId) throws RefundProcessingException;
}

public interface RejectRefundUseCase {
    /**
     * Rejects a refund request with reason.
     * Sends notification to customer.
     */
    void rejectRefund(Long refundRequestId, String rejectionReason);
}
```

#### **Reporting Use Cases**

```java
public interface GenerateRevenueReportUseCase {
    /**
     * Generates revenue report: total revenue, by category, by payment method.
     * Date range configurable.
     * Returns DTO with data + chart URLs.
     */
    RevenueReportDTO generateRevenueReport(LocalDate fromDate, LocalDate toDate);
}

public interface GenerateProductReportUseCase {
    /**
     * Generates product performance report: top sellers, low performers, SKU breakdown.
     */
    ProductReportDTO generateProductReport(LocalDate fromDate, LocalDate toDate);
}

public interface GenerateCustomerReportUseCase {
    /**
     * Generates customer insights: new customers, repeat rate, churn analysis.
     */
    CustomerReportDTO generateCustomerReport(LocalDate fromDate, LocalDate toDate);
}
```

#### **Audit Log Use Case**

```java
public interface ViewAuditLogUseCase {
    /**
     * Views admin actions log: who, what, when.
     * Filters by admin, action type, date range.
     */
    PageResult<AuditLogEntryDTO> viewAuditLog(
        int page,
        int pageSize,
        AuditLogSearchCriteria criteria
    );
}
```

#### **Admin User Management Use Cases**

```java
public interface CreateAdminUserUseCase {
    /**
     * Super-admin only: creates new admin user.
     * Can assign roles: ADMIN, SUPER_ADMIN.
     */
    void createAdminUser(String email, String password, Set<AdminRole> roles);
}

public interface ListAdminUsersUseCase {
    /**
     * Lists all admin users and their roles/permissions.
     */
    PageResult<AdminUserDTO> listAdminUsers(int page, int pageSize);
}
```

### 4.2 Outbound Ports (Data Access & External Services)

All in `admin-domain/application/port/out/`:

#### **Query Ports (Read-Only, Complex Aggregations)**

```java
public interface DashboardMetricsPort {
    /**
     * Complex aggregation query:
     * SELECT
     *   SUM(total) as revenue_today,
     *   COUNT(*) as orders_today,
     *   (SELECT COUNT(*) FROM USER_ACCOUNT WHERE created_at > today) as new_customers,
     *   (SELECT status, COUNT(*) FROM ORDER_ENTITY GROUP BY status) as orders_by_status
     * WHERE created_at > today
     */
    DashboardMetricsDTO getDashboardMetrics();
}

public interface OrderQueryPort {
    /**
     * Lists orders with JOINs:
     * - USER_ACCOUNT (customer name, email)
     * - ORDER_LINE_ENTITY (item count)
     * - PAYMENT_TRANSACTION (payment status)
     * Pagination, filtering, sorting.
     */
    PageResult<OrderAdminDTO> findOrders(
        OrderAdminSearchCriteria criteria,
        int page,
        int pageSize
    );
    
    /**
     * Gets full order with all related data.
     */
    Optional<OrderDetailsDTO> findOrderDetails(Long orderId);
}

public interface ProductQueryPort {
    /**
     * Lists products with sales stats:
     * - ORDER_LINE_ENTITY.quantity SUM (units sold)
     * - ORDER_LINE_ENTITY.unit_price AVG (average price)
     * - PRODUCT_ENTITY.views COUNT (page views, if tracked)
     */
    PageResult<ProductAdminDTO> findProducts(
        ProductAdminSearchCriteria criteria,
        int page,
        int pageSize
    );
    
    Optional<ProductAdminDTO> findProductDetails(Long productId);
}

public interface UserQueryPort {
    /**
     * Lists users with customer insights:
     * - COUNT(ORDER_ENTITY) as orders_count
     * - SUM(ORDER_ENTITY.total) as lifetime_value
     * - MAX(last_login_at) as last_login
     */
    PageResult<CustomerAdminDTO> findUsers(
        UserAdminSearchCriteria criteria,
        int page,
        int pageSize
    );
    
    Optional<CustomerAdminDTO> findUserDetails(Long userId);
}

public interface AuditQueryPort {
    /**
     * Lists audit log entries.
     * Filters: admin user, action type, date range, entity type.
     */
    PageResult<AuditLogEntryDTO> findAuditLog(
        AuditLogSearchCriteria criteria,
        int page,
        int pageSize
    );
}

public interface RefundPort {
    /**
     * Gets refund requests with joins to orders and customers.
     */
    PageResult<RefundRequestDTO> findRefundRequests(
        RefundSearchCriteria criteria,
        int page,
        int pageSize
    );
    
    Optional<RefundRequestDTO> findRefundDetails(Long refundId);
    
    /**
     * Updates refund status (PENDING → APPROVED → PROCESSED).
     */
    void updateRefundStatus(Long refundId, RefundStatus status);
}
```

#### **Report Generation Port**

```java
public interface ReportPort {
    /**
     * Exports data to PDF.
     * Takes report data + template name + options (color, logo, etc.)
     */
    byte[] generatePDF(Object reportData, String templateName, ReportOptions options)
        throws ReportGenerationException;
    
    /**
     * Exports data to CSV.
     * Takes list of objects + CSV template.
     */
    byte[] generateCSV(List<?> data, String templateName)
        throws ReportGenerationException;
}
```

#### **Payment Gateway Port (for Refunds)**

```java
public interface PaymentGatewayPort {
    /**
     * Called by RefundPort to reverse a charge.
     * Takes original transaction ID + refund amount.
     * Returns refund transaction ID from payment processor.
     */
    String processRefund(String originalTransactionId, BigDecimal amount)
        throws PaymentFailedException;
}
```

### 4.3 DTOs (Data Transfer Objects)

All in `admin-domain/application/dto/`:

**Immutable Records or Final Classes** — no setters.

#### **Dashboard DTOs**

```java
public final class DashboardMetricsDTO {
    public final BigDecimal revenueToday;
    public final BigDecimal revenueThisMonth;
    public final BigDecimal revenueYTD;
    public final Integer ordersToday;
    public final Integer ordersThisMonth;
    public final Integer ordersYTD;
    public final Integer newCustomersToday;
    public final Integer newCustomersThisMonth;
    public final Integer newCustomersYTD;
    public final Map<OrderStatus, Integer> ordersByStatus;
    public final BigDecimal averageOrderValue;
    public final Double conversionRate;
    public final Double cartAbandonmentRate;
    public final Instant calculatedAt;
    public final Instant nextRefreshAt;
    
    // Constructor, getters
}
```

#### **Order DTOs**

```java
public final class OrderAdminDTO {
    public final Long id;
    public final String orderNumber;  // ORD-20260729-0001
    public final Long customerId;
    public final String customerName;
    public final String customerEmail;
    public final BigDecimal total;
    public final OrderStatus status;
    public final LocalDateTime createdAt;
    public final LocalDateTime lastUpdatedAt;
    public final Integer itemCount;
    public final PaymentStatus paymentStatus;
    public final String trackingNumber;  // if shipped
    
    // getters, equals, hashCode
}

public final class OrderDetailsDTO {
    public final Long id;
    public final String orderNumber;
    public final Long customerId;
    public final String customerName;
    public final String customerEmail;
    public final String customerPhone;
    public final List<OrderLineDTO> items;
    public final ShippingAddressDTO shippingAddress;
    public final BillingAddressDTO billingAddress;
    public final BigDecimal subtotal;
    public final BigDecimal shippingCost;
    public final BigDecimal taxAmount;
    public final BigDecimal total;
    public final OrderStatus status;
    public final PaymentStatus paymentStatus;
    public final List<PaymentTransactionDTO> paymentTransactions;
    public final String shippingMethod;
    public final String trackingNumber;
    public final List<TimelineEventDTO> timeline;  // status changes, admin notes
    public final LocalDateTime createdAt;
    public final LocalDateTime lastUpdatedAt;
    
    // getters, equals, hashCode
}

public final class OrderLineDTO {
    public final Long productId;
    public final String productName;
    public final String sku;
    public final Integer quantity;
    public final BigDecimal unitPrice;
    public final BigDecimal lineTotal;
    
    // getters
}

public final class PaymentTransactionDTO {
    public final Long id;
    public final String transactionId;  // from payment gateway
    public final PaymentType type;  // AUTHORIZE, CAPTURE, REFUND
    public final PaymentStatus status;  // SUCCESS, FAILED, PENDING
    public final BigDecimal amount;
    public final LocalDateTime timestamp;
    public final String gatewayResponse;  // JSON from Stripe/PagSeguro
    
    // getters
}

public final class TimelineEventDTO {
    public final String eventType;  // ORDER_CREATED, STATUS_CHANGED, PAYMENT_RECEIVED, SHIPPED, REFUND_APPROVED
    public final LocalDateTime timestamp;
    public final String description;
    public final Long adminUserId;  // who made the change (null if system)
    public final String adminName;
    
    // getters
}

public final class ShippingAddressDTO {
    public final String street;
    public final String number;
    public final String complement;
    public final String neighborhood;
    public final String city;
    public final String state;
    public final String postalCode;
    
    // getters
}
```

#### **Product DTOs**

```java
public final class ProductAdminDTO {
    public final Long id;
    public final String sku;
    public final String name;
    public final String description;
    public final List<String> categoryNames;
    public final BigDecimal price;
    public final BigDecimal costPrice;  // admin view only
    public final Integer quantityInStock;
    public final Integer unitsSold;
    public final BigDecimal totalRevenue;  // unitsSold * avgPrice
    public final Double averageRating;
    public final Integer reviewCount;
    public final Boolean isActive;
    public final LocalDateTime createdAt;
    public final LocalDateTime lastUpdatedAt;
    public final List<String> imageUrls;
    
    // getters
}
```

#### **Customer DTOs**

```java
public final class CustomerAdminDTO {
    public final Long id;
    public final String email;
    public final String fullName;
    public final String phoneNumber;
    public final LocalDateTime createdAt;
    public final LocalDateTime lastLoginAt;
    public final Boolean isActive;
    public final Boolean isBlocked;
    public final Integer totalOrders;
    public final BigDecimal lifetimeValue;
    public final BigDecimal averageOrderValue;
    public final List<OrderAdminDTO> recentOrders;  // last 5
    public final List<AddressDTO> addresses;
    public final Set<String> roles;  // CUSTOMER, ADMIN
    
    // getters
}

public final class AddressDTO {
    public final Long id;
    public final String street;
    public final String number;
    public final String complement;
    public final String neighborhood;
    public final String city;
    public final String state;
    public final String postalCode;
    public final String label;
    public final Boolean isDefault;
    
    // getters
}
```

#### **Refund DTOs**

```java
public final class RefundRequestDTO {
    public final Long id;
    public final Long orderId;
    public final String orderNumber;
    public final Long customerId;
    public final String customerName;
    public final BigDecimal refundAmount;
    public final RefundStatus status;  // PENDING, APPROVED, REJECTED, PROCESSED
    public final String reason;
    public final LocalDateTime requestedAt;
    public final LocalDateTime approvedAt;
    public final Long approvedByAdminId;
    public final String approvedByAdminName;
    public final String rejectionReason;  // if REJECTED
    public final String refundTransactionId;  // if PROCESSED
    
    // getters
}
```

#### **Report DTOs**

```java
public final class RevenueReportDTO {
    public final BigDecimal totalRevenue;
    public final BigDecimal totalTax;
    public final BigDecimal totalShipping;
    public final BigDecimal netRevenue;
    public final Integer totalOrders;
    public final BigDecimal averageOrderValue;
    public final Map<String, BigDecimal> revenueByCategory;  // category → revenue
    public final Map<LocalDate, BigDecimal> revenueByDate;  // time series for chart
    public final Map<String, Integer> ordersByPaymentMethod;  // CREDIT_CARD, PIX, etc.
    public final List<Map<String, Object>> detailedTransactions;  // for table export
    public final LocalDate fromDate;
    public final LocalDate toDate;
    
    // getters
}

public final class ProductReportDTO {
    public final List<ProductPerformanceDTO> topSellers;  // top 10 by units
    public final List<ProductPerformanceDTO> topByRevenue;  // top 10 by revenue
    public final List<ProductPerformanceDTO> lowPerformers;  // bottom 10
    public final Map<String, Integer> unitsByCategory;
    public final Map<String, BigDecimal> revenueByCategory;
    public final Integer totalProductsActive;
    public final Integer totalProductsInactive;
    
    // getters
}

public final class ProductPerformanceDTO {
    public final Long productId;
    public final String sku;
    public final String name;
    public final Integer unitsSold;
    public final BigDecimal totalRevenue;
    public final BigDecimal costPrice;
    public final BigDecimal profitMargin;  // (totalRevenue - costPrice * unitsSold) / totalRevenue
    public final Double averageRating;
    
    // getters
}

public final class CustomerReportDTO {
    public final Integer totalCustomers;
    public final Integer newCustomersThisPeriod;
    public final Double repeatCustomerRate;  // % who ordered > 1 time
    public final BigDecimal averageCustomerLifetimeValue;
    public final Integer customersLast30Days;
    public final Integer customersLast90Days;
    public final Map<LocalDate, Integer> newCustomersByDate;  // time series
    
    // getters
}
```

#### **Audit Log DTOs**

```java
public final class AuditLogEntryDTO {
    public final Long id;
    public final Long adminUserId;
    public final String adminName;
    public final String actionType;  // ORDER_STATUS_CHANGED, PRODUCT_CREATED, REFUND_APPROVED
    public final String entityType;  // ORDER, PRODUCT, REFUND, USER
    public final Long entityId;
    public final String description;  // "Order #123 status changed from PENDING to CONFIRMED"
    public final String oldValue;  // optional, for changes
    public final String newValue;  // optional, for changes
    public final LocalDateTime timestamp;
    public final String ipAddress;
    public final String userAgent;
    
    // getters
}
```

#### **Search Criteria DTOs**

```java
public final class OrderAdminSearchCriteria {
    public final OrderStatus status;  // nullable — filter by status
    public final Long customerId;  // nullable
    public final LocalDate fromDate;  // nullable
    public final LocalDate toDate;  // nullable
    public final String searchQuery;  // order number or customer name
    public final String sortBy;  // "createdAt", "total", "status" (default: createdAt DESC)
    
    // Constructors, getters
}

public final class ProductAdminSearchCriteria {
    public final String searchQuery;  // name, SKU, description
    public final String category;  // nullable
    public final Boolean isActive;  // nullable
    public final BigDecimal minPrice;  // nullable
    public final BigDecimal maxPrice;  // nullable
    public final String sortBy;  // "name", "units_sold", "revenue" (default: name)
    
    // Constructors, getters
}

public final class UserAdminSearchCriteria {
    public final String searchQuery;  // email, name
    public final Boolean isActive;  // nullable
    public final Boolean isBlocked;  // nullable
    public final LocalDate fromDate;  // account created after
    public final LocalDate toDate;  // account created before
    public final String sortBy;  // "lifetime_value" DESC, "created_at" DESC
    
    // Constructors, getters
}

public final class RefundSearchCriteria {
    public final RefundStatus status;  // PENDING, APPROVED, REJECTED, PROCESSED
    public final Long orderId;  // nullable
    public final Long customerId;  // nullable
    public final LocalDate fromDate;  // nullable
    public final LocalDate toDate;  // nullable
    
    // Constructors, getters
}

public final class AuditLogSearchCriteria {
    public final Long adminUserId;  // nullable
    public final String actionType;  // nullable
    public final String entityType;  // nullable
    public final LocalDate fromDate;  // nullable
    public final LocalDate toDate;  // nullable
    
    // Constructors, getters
}
```

---

## 5. Application Services (Orchestration & Authorization)

All in `admin-domain/application/service/`:

### 5.1 `AdminDashboardService.java` — Orchestrates All Use Cases

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
    private User currentUser;  // inject logged-in admin
    
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
    private AuditLogPort auditLog;  // to LOG admin actions
    
    /**
     * Authorization check: must be ADMIN.
     * Called at start of every use case.
     */
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
        // Call complex aggregation port
        return metrics.getDashboardMetrics();
    }
    
    @Override
    public PageResult<OrderAdminDTO> listOrders(
        int page,
        int pageSize,
        OrderAdminSearchCriteria criteria) {
        checkAdminRole();
        // Delegate to port
        PageResult<OrderAdminDTO> result = orderQuery.findOrders(criteria, page, pageSize);
        // Log action
        auditLog.logEvent(
            currentUser.id(),
            "ORDER_LIST_VIEWED",
            "Admin viewed orders with criteria: " + criteria.toString()
        );
        return result;
    }
    
    @Override
    public void updateOrderStatus(Long orderId, OrderStatus newStatus) {
        checkAdminRole();
        
        // 1. Fetch order
        OrderDetailsDTO order = orderQuery.findOrderDetails(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order " + orderId + " not found"));
        
        // 2. Validate status transition (use Order domain logic, if available)
        // For now, assume all transitions valid (or implement rules here)
        
        // 3. Update order status (via port — assumes order module has adapter for this)
        // Note: This requires Order module to expose an update port
        // For now, assume we call a method on refundPort or separate OrderUpdatePort
        
        // 4. Send notification to customer
        // notificationPort.sendOrderStatusUpdateEmail(order);
        
        // 5. Log action
        auditLog.logEvent(
            currentUser.id(),
            "ORDER_STATUS_CHANGED",
            "Order " + orderId + " status changed from " + order.status + " to " + newStatus
        );
    }
    
    @Override
    public void approveRefund(Long refundRequestId) throws RefundProcessingException {
        checkAdminRole();
        
        // 1. Fetch refund request
        RefundRequestDTO refund = refundPort.findRefundDetails(refundRequestId)
            .orElseThrow(() -> new OrderNotFoundException("Refund " + refundRequestId + " not found"));
        
        // 2. Process payment reversal (call PaymentGatewayPort)
        String refundTransactionId = paymentGateway.processRefund(
            refund.refundTransactionId,  // original transaction to reverse
            refund.refundAmount
        );
        
        // 3. Update refund status to PROCESSED
        refundPort.updateRefundStatus(refundRequestId, RefundStatus.PROCESSED);
        
        // 4. Send notification to customer
        // notificationPort.sendRefundApprovedEmail(...);
        
        // 5. Log action
        auditLog.logEvent(
            currentUser.id(),
            "REFUND_APPROVED",
            "Refund " + refundRequestId + " approved for order " + refund.orderId
        );
    }
    
    // ... implement all other use cases similarly
}
```

---

## 6. Adapter Layer (Complex Queries & Reporting)

### 6.1 `DashboardMetricsAdapter.java` — Complex Aggregation Queries

```java
@ApplicationScoped
public class DashboardMetricsAdapter implements DashboardMetricsPort {
    
    @Inject
    private EntityManager em;
    
    @Inject
    @ConfigProperty(name = "dashboard.metrics.cache.minutes", defaultValue = "5")
    private Integer cacheMinutes;
    
    @Override
    public DashboardMetricsDTO getDashboardMetrics() {
        // Check cache first (5-minute TTL)
        // DashboardMetricsDTO cached = cache.getIfPresent("dashboard-metrics");
        // if (cached != null && !cached.nextRefreshAt.isBefore(Instant.now())) {
        //     return cached;
        // }
        
        // Execute complex aggregation query
        // SELECT
        //   SUM(o.total) as revenue_today,
        //   COUNT(o.id) as orders_today,
        //   o.status, COUNT(*) as orders_by_status
        // FROM ORDER_ENTITY o
        // WHERE o.created_at >= CURRENT_DATE
        // GROUP BY o.status
        
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate yearStart = LocalDate.of(today.getYear(), 1, 1);
        
        // Revenue queries
        BigDecimal revenueToday = em.createQuery(
            "SELECT COALESCE(SUM(o.total), 0) FROM OrderJpaEntity o WHERE o.createdAt >= :today",
            BigDecimal.class
        )
            .setParameter("today", Instant.now().truncatedTo(ChronoUnit.DAYS))
            .getSingleResult();
        
        BigDecimal revenueThisMonth = em.createQuery(
            "SELECT COALESCE(SUM(o.total), 0) FROM OrderJpaEntity o WHERE o.createdAt >= :monthStart",
            BigDecimal.class
        )
            .setParameter("monthStart", monthStart.atStartOfDay(ZoneId.systemDefault()).toInstant())
            .getSingleResult();
        
        // Order count queries
        Integer ordersToday = em.createQuery(
            "SELECT COUNT(o) FROM OrderJpaEntity o WHERE o.createdAt >= :today",
            Integer.class
        )
            .setParameter("today", today.atStartOfDay(ZoneId.systemDefault()).toInstant())
            .getSingleResult();
        
        // Status breakdown
        List<Object[]> statusCounts = em.createQuery(
            "SELECT o.status, COUNT(o) FROM OrderJpaEntity o WHERE o.createdAt >= :today GROUP BY o.status",
            Object[].class
        )
            .setParameter("today", today.atStartOfDay(ZoneId.systemDefault()).toInstant())
            .getResultList();
        
        Map<OrderStatus, Integer> ordersByStatus = new EnumMap<>(OrderStatus.class);
        for (Object[] row : statusCounts) {
            ordersByStatus.put((OrderStatus) row[0], ((Number) row[1]).intValue());
        }
        
        // New customers query
        Integer newCustomersToday = em.createQuery(
            "SELECT COUNT(u) FROM UserJpaEntity u WHERE u.createdAt >= :today AND u.roles CONTAINS :customerRole",
            Integer.class
        )
            .setParameter("today", today.atStartOfDay(ZoneId.systemDefault()).toInstant())
            .setParameter("customerRole", Role.CUSTOMER)
            .getSingleResult();
        
        // Conversion rate (orders with checkout / sessions)
        // Assume tracking somewhere, or calculate as active_orders / total_users
        Double conversionRate = calculateConversionRate();
        
        // Build DTO
        DashboardMetricsDTO metrics = new DashboardMetricsDTO(
            revenueToday,
            revenueThisMonth,
            // ... other fields
            ordersByStatus,
            // ...
            Instant.now(),
            Instant.now().plus(Duration.ofMinutes(cacheMinutes))
        );
        
        // Cache result
        // cache.put("dashboard-metrics", metrics);
        
        return metrics;
    }
    
    private Double calculateConversionRate() {
        // Placeholder: implement based on business rules
        // e.g., (orders_count / unique_visitors_count) * 100
        return 2.5;  // 2.5% conversion
    }
}
```

### 6.2 `OrderQueryAdapter.java` — Complex Order Queries

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
        
        // Build dynamic query with JPA Criteria API or JPQL
        // SELECT
        //   o.id, o.created_at, o.status, o.total,
        //   u.first_name, u.last_name, u.email,
        //   (SELECT COUNT(*) FROM order_line WHERE order_id = o.id) as item_count
        // FROM order_entity o
        // JOIN user_account u ON o.customer_id = u.id
        // WHERE (o.status = :status OR :status IS NULL)
        //   AND (o.customer_id = :customerId OR :customerId IS NULL)
        //   AND (o.created_at >= :fromDate OR :fromDate IS NULL)
        //   AND (o.created_at <= :toDate OR :toDate IS NULL)
        //   AND (CONCAT(o.order_number, u.first_name, u.last_name, u.email) LIKE :searchQuery OR :searchQuery IS NULL)
        // ORDER BY o.created_at DESC
        // LIMIT :pageSize OFFSET :offset
        
        StringBuilder jpql = new StringBuilder(
            "SELECT new admin.dto.OrderAdminDTO(" +
            "o.id, o.orderNumber, u.id, u.firstName, u.lastName, u.email, " +
            "o.total, o.status, o.createdAt, o.updatedAt, " +
            "(SELECT COUNT(ol) FROM OrderLineJpaEntity ol WHERE ol.order.id = o.id), " +
            "p.status) " +
            "FROM OrderJpaEntity o " +
            "JOIN o.customer u " +
            "LEFT JOIN PaymentTransactionJpaEntity p ON p.order.id = o.id " +
            "WHERE 1=1"
        );
        
        // Add filters
        if (criteria.status != null) {
            jpql.append(" AND o.status = :status");
        }
        if (criteria.customerId != null) {
            jpql.append(" AND u.id = :customerId");
        }
        if (criteria.fromDate != null) {
            jpql.append(" AND o.createdAt >= :fromDate");
        }
        if (criteria.toDate != null) {
            jpql.append(" AND o.createdAt < :toDate");
        }
        if (criteria.searchQuery != null && !criteria.searchQuery.isBlank()) {
            jpql.append(" AND (LOWER(o.orderNumber) LIKE :searchQuery OR " +
                       "LOWER(u.firstName) LIKE :searchQuery OR " +
                       "LOWER(u.lastName) LIKE :searchQuery OR " +
                       "LOWER(u.email) LIKE :searchQuery)");
        }
        
        jpql.append(" ORDER BY o.").append(criteria.sortBy != null ? criteria.sortBy : "createdAt DESC");
        
        // Create query
        TypedQuery<OrderAdminDTO> query = em.createQuery(jpql.toString(), OrderAdminDTO.class);
        
        // Set parameters
        if (criteria.status != null) {
            query.setParameter("status", criteria.status);
        }
        if (criteria.customerId != null) {
            query.setParameter("customerId", criteria.customerId);
        }
        if (criteria.fromDate != null) {
            query.setParameter("fromDate", criteria.fromDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        }
        if (criteria.toDate != null) {
            query.setParameter("toDate", criteria.toDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        }
        if (criteria.searchQuery != null && !criteria.searchQuery.isBlank()) {
            query.setParameter("searchQuery", "%" + criteria.searchQuery.toLowerCase() + "%");
        }
        
        // Total count
        Long totalCount = em.createQuery(
            "SELECT COUNT(o) FROM OrderJpaEntity o WHERE 1=1 " + // add same filters
            (criteria.status != null ? " AND o.status = :status" : "") +
            (criteria.customerId != null ? " AND o.customerId = :customerId" : ""),
            Long.class
        ).getSingleResult();
        
        // Pagination
        List<OrderAdminDTO> results = query
            .setFirstResult((page - 1) * pageSize)
            .setMaxResults(pageSize)
            .getResultList();
        
        return new PageResult<>(results, totalCount.intValue(), page, pageSize);
    }
    
    @Override
    public Optional<OrderDetailsDTO> findOrderDetails(Long orderId) {
        // Complex query with all joins
        OrderDetailsDTO details = em.createQuery(
            "SELECT new admin.dto.OrderDetailsDTO(...) " +
            "FROM OrderJpaEntity o " +
            "JOIN FETCH o.lines " +
            "JOIN FETCH o.customer " +
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

### 6.3 `ReportGeneratorAdapter.java` — PDF & CSV Export

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
            
            // Convert HTML → PDF using iText or Flying Saucer
            // Configure: logo (if options.includeLogo), colors, header/footer
            
            ByteArrayOutputStream pdfStream = new ByteArrayOutputStream();
            // renderer.render(html, pdfStream, options);
            
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
            // Use Apache Commons CSV or OpenCSV
            StringWriter output = new StringWriter();
            CSVPrinter printer = new CSVPrinter(output, CSVFormat.DEFAULT);
            
            // Write header (from template or data class fields)
            // Write data rows
            
            return output.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate CSV: " + e.getMessage(), e);
        }
    }
}
```

---

## 7. Web Layer (JSF Beans & Pages)

### 7.1 `DashboardBean.java` — Home Dashboard

```java
@Named("dashboardBean")
@ViewScoped
@RolesAllowed("ADMIN")
public class DashboardBean implements Serializable {
    
    @Inject
    private GetDashboardMetricsUseCase getDashboardMetrics;
    
    private DashboardMetricsDTO metrics;
    
    @PostConstruct
    void loadDashboard() {
        try {
            metrics = getDashboardMetrics.getDashboardMetrics();
        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error", e.getMessage()));
        }
    }
    
    public DashboardMetricsDTO getMetrics() { return metrics; }
    
    public String getRevenueFormattedCurrency() {
        // Format as currency (R$ for Brazil)
        return "R$ " + new DecimalFormat("#,##0.00").format(metrics.revenueToday);
    }
}
```

### 7.2 JSF Pages (`dashboard.xhtml`, `orders/list.xhtml`, etc.)

**`admin/dashboard.xhtml`** — Home with KPI cards and charts

```xhtml
<h:panelGroup layout="block" styleClass="admin-dashboard">
    <h1>Admin Dashboard</h1>
    
    <!-- KPI Cards -->
    <div class="metrics-row">
        <div class="metric-card">
            <h3>Revenue Today</h3>
            <p class="metric-value">#{dashboardBean.metrics.revenueFormattedCurrency}</p>
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
    
    <!-- Charts (PrimeFaces or Chart.js) -->
    <p:chart type="line" model="#{dashboardBean.revenueChart}" />
    <p:chart type="bar" model="#{dashboardBean.ordersByStatusChart}" />
    
    <!-- Quick Actions -->
    <div class="quick-actions">
        <h:link outcome="orders/list">View All Orders</h:link>
        <h:link outcome="refunds/list">Pending Refunds</h:link>
        <h:link outcome="products/list">Manage Products</h:link>
    </div>
</h:panelGroup>
```

**`admin/orders/list.xhtml`** — Order Management List

```xhtml
<h:form id="orderForm">
    <h1>Orders</h1>
    
    <!-- Filters -->
    <div class="filters">
        <h:selectOneMenu value="#{orderManagementBean.searchCriteria.status}">
            <f:selectItem itemLabel="All Statuses" itemValue="#{null}" />
            <f:selectItems value="#{orderManagementBean.orderStatuses}" />
        </h:selectOneMenu>
        
        <h:inputText value="#{orderManagementBean.searchCriteria.searchQuery}" 
                     placeholder="Search by order number or customer name" />
        
        <h:commandButton value="Search" action="#{orderManagementBean.search}" update="orderTable" />
    </div>
    
    <!-- Table -->
    <p:dataTable id="orderTable" value="#{orderManagementBean.orders}" var="order" paginator="true" rows="20">
        <p:column headerText="Order #">#{order.orderNumber}</p:column>
        <p:column headerText="Customer">#{order.customerName} (#{order.customerEmail})</p:column>
        <p:column headerText="Total">R$ #{order.total}</p:column>
        <p:column headerText="Status">
            <h:outputText value="#{order.status}" 
                         styleClass="status-#{order.status.toLowerCase()}" />
        </p:column>
        <p:column headerText="Date">#{order.createdAt}</p:column>
        <p:column headerText="Actions">
            <h:commandLink value="View" outcome="detail">
                <f:param name="orderId" value="#{order.id}" />
            </h:commandLink>
        </p:column>
    </p:dataTable>
</h:form>
```

**`admin/orders/detail.xhtml`** — Order Detail View

```xhtml
<h:panelGroup layout="block" styleClass="admin-order-detail">
    <h1>Order #{orderManagementBean.orderDetails.orderNumber}</h1>
    
    <!-- Order Info -->
    <h2>Order Details</h2>
    <dl>
        <dt>Customer:</dt>
        <dd>#{orderManagementBean.orderDetails.customerName} (#{orderManagementBean.orderDetails.customerEmail})</dd>
        
        <dt>Status:</dt>
        <dd>
            <h:selectOneMenu value="#{orderManagementBean.selectedStatus}" 
                            onchange="submit()">
                <f:selectItems value="#{orderManagementBean.validStatusTransitions}" />
                <f:ajax listener="#{orderManagementBean.updateOrderStatus}" update="@form" />
            </h:selectOneMenu>
        </dd>
        
        <dt>Total:</dt>
        <dd>R$ #{orderManagementBean.orderDetails.total}</dd>
    </dl>
    
    <!-- Items -->
    <h2>Items</h2>
    <h:dataTable value="#{orderManagementBean.orderDetails.items}" var="item">
        <h:column>
            <f:facet name="header">Product</f:facet>
            #{item.productName} (#{item.sku})
        </h:column>
        <h:column>
            <f:facet name="header">Qty</f:facet>
            #{item.quantity}
        </h:column>
        <h:column>
            <f:facet name="header">Unit Price</f:facet>
            R$ #{item.unitPrice}
        </h:column>
        <h:column>
            <f:facet name="header">Total</f:facet>
            R$ #{item.lineTotal}
        </h:column>
    </h:dataTable>
    
    <!-- Shipping Address -->
    <h2>Shipping Address</h2>
    <address>
        #{orderManagementBean.orderDetails.shippingAddress.street}, 
        #{orderManagementBean.orderDetails.shippingAddress.number}
        <br/>
        #{orderManagementBean.orderDetails.shippingAddress.city}, 
        #{orderManagementBean.orderDetails.shippingAddress.state}
        <br/>
        #{orderManagementBean.orderDetails.shippingAddress.postalCode}
    </address>
    
    <!-- Payment Transactions -->
    <h2>Payment Transactions</h2>
    <h:dataTable value="#{orderManagementBean.orderDetails.paymentTransactions}" var="txn">
        <h:column>
            <f:facet name="header">Type</f:facet>
            #{txn.type}
        </h:column>
        <h:column>
            <f:facet name="header">Status</f:facet>
            #{txn.status}
        </h:column>
        <h:column>
            <f:facet name="header">Amount</f:facet>
            R$ #{txn.amount}
        </h:column>
        <h:column>
            <f:facet name="header">Date</f:facet>
            #{txn.timestamp}
        </h:column>
    </h:dataTable>
    
    <!-- Timeline -->
    <h2>Timeline</h2>
    <ul class="timeline">
        <ui:repeat value="#{orderManagementBean.orderDetails.timeline}" var="event">
            <li>
                <strong>#{event.timestamp}</strong> — #{event.description}
                <c:if test="#{event.adminName != null}">
                    <em>by #{event.adminName}</em>
                </c:if>
            </li>
        </ui:repeat>
    </ul>
    
    <!-- Actions -->
    <h:form>
        <h:commandButton value="Process Refund" 
                        onclick="PF('refundDialog').show();" 
                        rendered="#{orderManagementBean.canProcessRefund}" />
        <h:link outcome="orders/list" value="Back to Orders" />
    </h:form>
</h:panelGroup>
```

---

## 8. Database Queries & Indexes

Since Admin Dashboard uses **read-only queries** on existing tables (ORDER_ENTITY, PRODUCT_ENTITY, USER_ACCOUNT, etc.), we add **indexes for query performance**:

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

-- Refund queries (if refund table exists)
CREATE INDEX idx_refund_status_created ON REFUND_REQUEST(status, created_at DESC);
CREATE INDEX idx_refund_order ON REFUND_REQUEST(order_id);
```

---

## 9. Security & Access Control

- ✅ **@RolesAllowed("ADMIN")** on all Admin Dashboard beans
- ✅ **Container-level enforcement** (not just UI checks)
- ✅ **@CurrentUser injection** ensures only logged-in admin can act
- ✅ **Audit logging** of all admin actions (who did what when)
- ✅ **No sensitive data exposure** — costs hidden from customers, shown to admins
- ✅ **HTTPS-only** (enforced at deployment)

---

## 10. Caching Strategy

Admin Dashboard performs **expensive aggregation queries**. Implement caching:

**DashboardMetricsDTO:**
- Cache TTL: 5 minutes (configurable)
- Invalidate on: order creation, order status change, new user registration
- Use: Guava Cache or Caffeine (in-memory) or Redis (distributed)

**Order/Product/User queries:**
- Cache TTL: 1 minute
- Invalidate on: data modification

**Reports:**
- Cache TTL: 30 minutes
- Invalidate manually (full report regeneration slow)

---

## 11. Testing Requirements

**Domain Layer Tests** (minimal, mostly DTOs):
- DTO immutability
- Search criteria validation
- Exception cases

**Adapter Tests**:
- Complex query correctness (Testcontainers with real DB)
- Report generation (PDF, CSV correctness)
- Refund processing (mocked payment gateway)
- Audit logging

**Integration Tests**:
- E2E admin workflows: view dashboard → click order → update status → see audit log
- Report generation: select date range → download PDF
- Refund flow: view refund request → approve → see payment reversal

**Performance Tests**:
- Dashboard metrics query < 1 second (with indexes)
- Order list pagination < 500ms
- Report generation < 5 seconds

---

## 12. Performance & Scalability Considerations

- ✅ **Indexes on all query filters** (status, customer_id, date, etc.)
- ✅ **Pagination** on all lists (default 20 rows)
- ✅ **Caching** of expensive metrics (5-min TTL)
- ✅ **N+1 prevention:** JOIN FETCH in queries
- ✅ **Read replicas:** (Phase 2) Route admin queries to read-only DB replica
- ✅ **Background jobs:** Refresh dashboard metrics every 5 min (via scheduler)

---

## 13. Future Phase 2 (Deferred, Not in Scope)

- Advanced filtering (date ranges, custom reports)
- Multi-store admin (manage multiple storefronts)
- Admin role granularity (view-only vs edit-only roles)
- Webhook management (trigger actions on order status change)
- API key management (third-party integrations)
- Admin notifications (Slack, email alerts for high-value orders)

---

## 14. Open Questions / Assumptions for Review

1. ✅ **Data visibility:** Admins see ALL customer data (emails, phone, addresses)? Or redacted? (**Answer: Full visibility for MVP**)
2. ✅ **Refund approval:** Automatic or manual? (**Answer: Manual approval for MVP**)
3. ✅ **Report format:** PDF + CSV? Or more? (**Answer: PDF + CSV for MVP**)
4. ✅ **Cache provider:** Guava Cache (in-memory, single server) or Redis (distributed)? (**Answer: Guava Cache for MVP, Redis Phase 2**)
5. ✅ **Concurrent admin access:** Multiple admins can edit same order simultaneously? (**Answer: Yes, optimistic locking on admin actions**)
6. ✅ **Audit retention:** How long keep audit logs? (**Answer: Indefinite for MVP, archival Phase 2**)
7. ✅ **Super-admin role:** Is there a super-admin who can create/delete other admins? (**Answer: Yes, separate SUPER_ADMIN role**)
8. ✅ **Charts library:** PrimeFaces Charts, Chart.js, or Apache ECharts? (**Answer: PrimeFaces Charts for simplicity**)
