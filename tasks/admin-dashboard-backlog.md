# Admin Dashboard Module — Agile Backlog Refinement

**Companion to:** `admin-dashboard-module-spec.md` (technical design) and `admin-dashboard-implementation-sequence.md` (build order).

**Purpose of this document:** Break down the Admin Dashboard module into independently valuable, shippable stories with Given/When/Then acceptance criteria, Definition of Ready, and Definition of Done — enabling incremental delivery and parallel work.

---

## Epic: Admin Dashboard & Back-Office Operations

**Epic goal:** Enable administrators to manage e-commerce operations via a comprehensive back-office dashboard. Provide real-time visibility into orders, products, customers, and revenue. Support critical operational tasks (order status updates, refund processing, product management).

**Epic-level Definition of Done:** All stories below are done; admins can view dashboard with KPIs; order management (list, filter, detail, status update) works; product management works; refund processing works; reports generate; audit logging captures all actions; RBAC enforces admin-only access; ArchUnit tests pass.

---

## Story Map (Dependency Order & Swim Lanes)

```
┌─────────────────────────────────────────────────────────────────┐
│                 ADMIN DASHBOARD EPIC - STORY MAP                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│ FOUNDATION (Database & Queries)                                  │
│   S1: Create admin query adapters ─────────────────────────────┐ │
│   S2: Create complex aggregation queries (metrics) ────────────┤ │
│   S3: Create order/product/user query adapters ───────────────┤ │
│                                                                 │ │
│ DASHBOARD HOME (Visibility)                                    │ │
│   S4: Dashboard home with KPI metrics ◀─────────────────────────┤ │
│   S5: Dashboard charts (revenue, orders, status) ───────────────┤ │
│                                                                 │ │
│ ORDER MANAGEMENT (Critical Operations)                         │ │
│   S6: Order list with filters & pagination ◀──────────────────┐ │
│   S7: Order detail view with items & timeline ─────────────────┤ │
│   S8: Update order status + customer notification ─────────────┤ │
│   S9: Order refund processing ────────────────────────────────── │
│                                                                 │ │
│ PRODUCT MANAGEMENT (Catalog Control)                           │ │
│   S10: Product list (admin view, sales stats) ◀────────────────┐ │
│   S11: Create product (form, validation) ──────────────────────┤ │
│   S12: Edit/update product ────────────────────────────────────┤ │
│   S13: Deactivate/activate products (soft-delete) ─────────────┤ │
│                                                                 │ │
│ CUSTOMER MANAGEMENT (User Admin)                               │ │
│   S14: Customer list with LTV & order history ◀────────────────┐ │
│   S15: Customer detail view ───────────────────────────────────┤ │
│   S16: Block/unblock customer accounts ────────────────────────┤ │
│                                                                 │ │
│ REFUND MANAGEMENT (Financial)                                  │ │
│   S17: Refund requests list ◀──────────────────────────────────┐ │
│   S18: Approve refund (payment reversal) ──────────────────────┤ │
│   S19: Reject refund ──────────────────────────────────────────┤ │
│                                                                 │ │
│ REPORTING & ANALYTICS (Business Intelligence)                  │ │
│   S20: Revenue report (date range, breakdown) ◀─────────────────┤ │
│   S21: Product performance report ────────────────────────────┤ │
│   S22: Customer insights report ──────────────────────────────┤ │
│   S23: Report export (PDF, CSV) ──────────────────────────────┤ │
│                                                                 │ │
│ MONITORING & SECURITY (Audit & Control)                        │ │
│   S24: Audit log viewer ◀────────────────────────────────────┐ │ │
│   S25: Admin user management (create/list/roles) ────────────┤ │ │
│   S26: RBAC enforcement (@RolesAllowed, authorization checks) ┤ │ │
│   S27: ArchUnit admin boundary tests ──────────────────────────┤ │ │
│                                                                 │ │
└─────────────────────────────────────────────────────────────────┘
```

---

## Story Breakdown (27 Stories Total)

### **FOUNDATION SWIM LANE**

#### S1 — Create Admin Query Adapters & DTOs

**As** the system, **I want** query adapters that fetch data from existing modules (Order, Product, User), **so that** admin features can access rich data without violating module boundaries.

**Priority:** Must (MoSCoW)

**Definition of Ready:**
- [ ] Order module, Product module, User module all implemented and deployed
- [ ] Existing JPA entities (OrderJpaEntity, ProductJpaEntity, UserJpaEntity) reviewed
- [ ] DTO design (OrderAdminDTO, ProductAdminDTO, CustomerAdminDTO) finalized
- [ ] Database schema for Order, Product, User tables confirmed

**Acceptance Criteria:**

- **Given** an OrderAdminDTO, **when** fields are set, **then** all fields (id, orderNumber, customerId, customerName, customerEmail, total, status, createdAt, itemCount, paymentStatus) are present and immutable.
- **Given** a ProductAdminDTO, **when** instantiated, **then** it includes sales stats (unitsSold, totalRevenue, averageRating, reviewCount).
- **Given** a CustomerAdminDTO, **when** created, **then** it includes LTV (lifetimeValue), order count, average order value, and recent orders.
- **Given** a RefundRequestDTO, **when** populated, **then** it contains order details, customer info, refund amount, status, and approval metadata.
- **Given** a RevenueReportDTO, **when** generated, **then** it includes total revenue, breakdown by category, by payment method, time series for charting.
- **Given** a ProductReportDTO, **when** created, **then** it includes top sellers, low performers, SKU breakdown by category.
- **Given** DTOs, **when** they are inspected, **then** all are immutable records or final classes with no setters.

**Definition of Done:**
- [ ] All DTOs compile (27+ DTOs total)
- [ ] DTOs are immutable (final classes or records)
- [ ] All DTOs have comprehensive Javadoc
- [ ] SearchCriteria DTOs (OrderAdminSearchCriteria, ProductAdminSearchCriteria, UserAdminSearchCriteria, RefundSearchCriteria, AuditLogSearchCriteria) created
- [ ] PageResult<T> generic DTO supports pagination
- [ ] All exception classes created (UnauthorizedAdminAccessException, OrderNotFoundException, ProductNotFoundException, RefundProcessingException, ReportGenerationException)

**Story Points:** 8

---

#### S2 — Create Complex Aggregation Queries (Dashboard Metrics)

**As an** admin, **I want** to see key metrics on the dashboard (revenue, orders, new customers), **so that** I have real-time visibility into business performance.

**Priority:** Must

**Definition of Ready:**
- [ ] S1 merged (DTOs created)
- [ ] DashboardMetricsDTO finalized
- [ ] Database indexes for ORDER_ENTITY, USER_ACCOUNT defined
- [ ] Caching strategy (TTL, invalidation) decided (Guava Cache for MVP)

**Acceptance Criteria:**

- **Given** the dashboard metrics query, **when** called at 09:00 AM, **then** revenueToday = SUM(ORDER.total) WHERE created_at >= 2026-07-29 00:00:00.
- **Given** multiple orders placed today, **when** getDashboardMetrics is called, **then** revenueToday reflects all orders.
- **Given** orders with different statuses (PENDING, CONFIRMED, SHIPPED), **when** query aggregates, **then** ordersByStatus map has correct counts for each status.
- **Given** dashboard metrics query, **when** executed, **then** completes in < 1 second (with indexes).
- **Given** metrics cached for 5 minutes, **when** called again within 5 min, **then** cached result is returned (not requeried).
- **Given** metrics cache expires after 5 minutes, **when** next call comes, **then** fresh query is executed and cached.
- **Given** new user registered today, **when** dashboard metrics query runs, **then** newCustomersToday increments correctly.

**Definition of Done:**
- [ ] DashboardMetricsAdapter.getDashboardMetrics() implemented
- [ ] Complex JPQL/Criteria query tested with Testcontainers
- [ ] Query performance verified (< 1 second with test data)
- [ ] Caching implemented (Guava Cache, 5-min TTL, invalidation on order/user changes)
- [ ] DashboardMetricsAdapterTest passes with 10+ test cases
- [ ] All SQL queries explain-planned for index usage verification
- [ ] Metrics DTO includes: revenueToday, revenueThisMonth, revenueYTD, ordersToday, ordersThisMonth, newCustomers, ordersByStatus, averageOrderValue, conversionRate, cartAbandonmentRate, calculatedAt, nextRefreshAt

**Story Points:** 8

---

#### S3 — Create Order/Product/User Query Adapters

**As** the system, **I want** specialized query adapters for Orders, Products, and Users, **so that** admin features can list, search, filter, and paginate through large datasets efficiently.

**Priority:** Must

**Definition of Ready:**
- [ ] S1, S2 merged
- [ ] Database indexes on status, customer_id, created_at, product_id finalized
- [ ] SearchCriteria DTOs finalized
- [ ] Pagination defaults (20 rows/page) confirmed

**Acceptance Criteria:**

- **Given** OrderQueryAdapter.findOrders() with no filters, **when** called, **then** returns all orders paginated (20 per page).
- **Given** OrderQueryAdapter.findOrders() with status=PENDING, **when** called, **then** returns only PENDING orders.
- **Given** OrderQueryAdapter.findOrders() with search query "john", **when** called, **then** returns orders where order number or customer name/email contains "john" (case-insensitive).
- **Given** OrderQueryAdapter.findOrders() on page 2 of 100, **when** called, **then** returns orders 21-40 with totalPages=100.
- **Given** ProductQueryAdapter.findProducts() with category filter, **when** called, **then** returns only products in that category with sales counts.
- **Given** ProductQueryAdapter.findProducts() sorted by "units_sold" DESC, **when** called, **then** top sellers appear first.
- **Given** UserQueryAdapter.findUsers() with isActive=true filter, **when** called, **then** returns only active users (not blocked, not inactive).
- **Given** UserQueryAdapter.findUsers() sorted by "lifetime_value" DESC, **when** called, **then** highest-value customers appear first.
- **Given** queries with complex joins (ORDER → USER, PRODUCT → CATEGORY, etc.), **when** executed, **then** N+1 problem is avoided (JOIN FETCH or @EntityGraph used).
- **Given** queries with multiple filters, **when** executed, **then** all filters applied correctly (AND logic).

**Definition of Done:**
- [ ] OrderQueryAdapter implements OrderQueryPort (findOrders, findOrderDetails)
- [ ] ProductQueryAdapter implements ProductQueryPort (findProducts, findProductDetails)
- [ ] UserQueryAdapter implements UserQueryPort (findUsers, findUserDetails)
- [ ] AuditQueryAdapter implements AuditQueryPort (findAuditLog)
- [ ] All adapters tested with Testcontainers (real DB, 1000+ test records)
- [ ] Query performance verified (pagination queries < 500ms)
- [ ] No N+1 queries (join-fetch or entity graph verified)
- [ ] All search/filter combinations tested
- [ ] Sorting works on all specified columns (createdAt, total, status, name, unitsSold, lifetimeValue)
- [ ] Case-insensitive search verified for text fields

**Story Points:** 8

---

### **DASHBOARD HOME SWIM LANE**

#### S4 — Dashboard Home with KPI Metrics

**As an** admin, **I want** to see a dashboard home page showing key metrics (revenue, orders, customers), **so that** I get a quick overview of business health on login.

**Priority:** Must

**Depends on:** S1, S2

**Definition of Ready:**
- [ ] S2 merged (metrics queries working)
- [ ] Dashboard KPI card design approved
- [ ] Layout mockup reviewed

**Acceptance Criteria:**

- **Given** an admin logs in, **when** they navigate to /admin/dashboard, **then** page loads showing metrics cards: Revenue Today, Orders Today, New Customers, Avg Order Value.
- **Given** metrics cards on dashboard, **when** page renders, **then** each card shows: metric name, metric value (formatted), and trend (up/down arrow with % change from yesterday).
- **Given** revenue metric, **when** displayed, **then** formatted as currency "R$ 1,234.56".
- **Given** orders metric, **when** displayed, **then** shown as integer count "42".
- **Given** order status breakdown chart, **when** rendered, **then** pie chart shows: PENDING (10), CONFIRMED (20), PROCESSING (8), SHIPPED (3), DELIVERED (1).
- **Given** conversion rate metric, **when** shown, **then** displayed as percentage "2.5%".
- **Given** cart abandonment rate, **when** shown, **then** displayed as percentage "15.2%".

**Definition of Done:**
- [ ] DashboardBean (@ViewScoped, @RolesAllowed("ADMIN")) created
- [ ] dashboard.xhtml page created with KPI cards
- [ ] GetDashboardMetricsUseCase injected and called in @PostConstruct
- [ ] Metrics formatted correctly (currency, percentage, numbers)
- [ ] Error handling: if metrics query fails, FacesMessage displays error
- [ ] Page loads in < 2 seconds (cached metrics)
- [ ] Responsive design (mobile-friendly card layout)
- [ ] Non-admin users cannot access page (404 or redirect)

**Story Points:** 5

---

#### S5 — Dashboard Charts (Revenue, Orders, Status Breakdown)

**As an** admin, **I want** to see charts on the dashboard showing trends, **so that** I can visualize business performance over time.

**Priority:** Should

**Depends on:** S4

**Definition of Ready:**
- [ ] S4 merged
- [ ] Chart library chosen (PrimeFaces Charts)
- [ ] Chart data format defined (time series, category breakdown)

**Acceptance Criteria:**

- **Given** a revenue line chart, **when** displayed, **then** shows revenue for last 30 days (one point per day).
- **Given** a revenue chart, **when** hovered, **then** tooltip shows date and exact revenue amount.
- **Given** an order status bar chart, **when** rendered, **then** shows counts: PENDING, CONFIRMED, PROCESSING, SHIPPED, DELIVERED, CANCELLED.
- **Given** a pie chart for payment methods, **when** displayed, **then** shows: Credit Card (60%), PIX (35%), Boleto (5%).
- **Given** charts on dashboard, **when** page refreshes (every 5 min), **then** data updates automatically.
- **Given** charts, **when** zoomed or filtered by date range, **then** user can drill down into specific periods.

**Definition of Done:**
- [ ] DashboardBean extended with chart data methods (getRevenueChart(), getOrdersByStatusChart(), etc.)
- [ ] PrimeFaces <p:chart> components integrated into dashboard.xhtml
- [ ] Chart data populated from DashboardMetricsDTO
- [ ] Charts responsive (mobile-friendly, auto-resize)
- [ ] Charts refresh on dashboard refresh (or via AJAX polling every 5 min)
- [ ] Tooltip/legend labels clear and user-friendly
- [ ] Charts render in < 500ms

**Story Points:** 5

---

### **ORDER MANAGEMENT SWIM LANE**

#### S6 — Order List with Filters & Pagination

**As an** admin, **I want** to view all orders in a sortable, filterable list, **so that** I can find specific orders quickly.

**Priority:** Must

**Depends on:** S1, S3

**Definition of Ready:**
- [ ] S3 merged (OrderQueryAdapter working)
- [ ] Order list page design approved
- [ ] Filters design (status, customer, date range, search) approved

**Acceptance Criteria:**

- **Given** an admin navigates to /admin/orders, **when** page loads, **then** displays paginated list of all orders (20 per page).
- **Given** order list, **when** displayed, **then** columns show: Order #, Customer Name, Customer Email, Total, Status, Created Date, Actions.
- **Given** list page, **when** status filter set to "PENDING", **then** only PENDING orders displayed.
- **Given** list page, **when** date range filter set (from 2026-07-01 to 2026-07-31), **then** only orders created in July displayed.
- **Given** list page, **when** search "john" entered, **then** orders where order number or customer name/email contains "john" displayed.
- **Given** 150 orders, **when** page 1 viewed, **then** orders 1-20 displayed with "Page 1 of 8" indicator.
- **Given** page 1, **when** user clicks "Page 2", **then** orders 21-40 displayed.
- **Given** list, **when** "Customer" column header clicked, **then** list resorts by customer name A-Z.
- **Given** list status column, **when** viewed, **then** status displayed as colored badge (PENDING=red, CONFIRMED=yellow, SHIPPED=blue, DELIVERED=green).
- **Given** order row, **when** clicked, **then** navigates to order detail page.

**Definition of Done:**
- [ ] OrderManagementBean (@ViewScoped, @RolesAllowed("ADMIN")) created
- [ ] orders/list.xhtml page created with filterable data table (PrimeFaces <p:dataTable>)
- [ ] ListOrdersUseCase injected and called in @PostConstruct with default criteria
- [ ] Filters: status dropdown, customer search, date range picker, order number search
- [ ] Pagination working (20 rows/page, navigation buttons)
- [ ] Sorting working on all columns
- [ ] Status badges styled with colors
- [ ] Search/filter form submits via AJAX (no full page reload)
- [ ] Filter results update table without refresh
- [ ] Table loads in < 500ms

**Story Points:** 8

---

#### S7 — Order Detail View with Items & Timeline

**As an** admin, **I want** to see complete order details including items, shipping address, payment transactions, and status timeline, **so that** I can understand order context before taking actions.

**Priority:** Must

**Depends on:** S6

**Definition of Ready:**
- [ ] S6 merged
- [ ] Order detail page design approved
- [ ] Timeline events design finalized

**Acceptance Criteria:**

- **Given** order detail page for order #123, **when** loaded, **then** displays: order number, customer name/email, order total, current status, created date.
- **Given** order detail, **when** viewed, **then** shows section "Items" with table: Product Name (SKU), Quantity, Unit Price, Line Total.
- **Given** order detail, **when** viewed, **then** shows "Shipping Address" with full address: street, number, complement, neighborhood, city, state, postal code.
- **Given** order detail, **when** viewed, **then** shows "Payment Information" with payment method (Credit Card, PIX, Boleto) and total amount.
- **Given** order detail, **when** viewed, **then** shows "Payment Transactions" table with: Type (AUTHORIZE, CAPTURE, REFUND), Status, Amount, Transaction ID, Date.
- **Given** order detail, **when** viewed, **then** shows "Timeline" of status changes: who changed it (admin name or "System"), when, and to what status.
- **Given** order with multiple refunds, **when** order detail viewed, **then** refund transactions displayed in Payment Transactions.
- **Given** order detail, **when** scrolled, **then** page renders in < 500ms.

**Definition of Done:**
- [ ] OrderManagementBean extended with getOrderDetails(Long orderId)
- [ ] orders/detail.xhtml page created
- [ ] OrderDetailsDTO populated with all required data (items, addresses, payment history, timeline)
- [ ] JSF binding of dto fields to page
- [ ] Sections: order info, items table, addresses, payment transactions table, timeline list
- [ ] Timeline styled as vertical list with icons (created, confirmed, shipped, delivered, refund icons)
- [ ] Payment transaction table shows all captured/refunded events
- [ ] Responsive design (mobile-friendly tables)
- [ ] Currency formatting for amounts (R$ XXX.XX)
- [ ] Date/time formatted per locale (pt_BR: dd/MM/yyyy HH:mm:ss)

**Story Points:** 8

---

#### S8 — Update Order Status + Customer Notification

**As an** admin, **I want** to change an order's status (PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED), **so that** I can manage order lifecycle and customers are notified of changes.

**Priority:** Must

**Depends on:** S7

**Definition of Ready:**
- [ ] S7 merged
- [ ] UpdateOrderStatusUseCase defined
- [ ] Valid status transition rules confirmed (PENDING→CONFIRMED, CONFIRMED→PROCESSING, etc.)
- [ ] NotificationPort available (from User Account module)
- [ ] Audit logging strategy confirmed

**Acceptance Criteria:**

- **Given** order detail page, **when** status dropdown changed from PENDING to CONFIRMED, **then** dropdown shows only valid transitions (CONFIRMED, CANCELLED).
- **Given** admin changes order status to SHIPPED, **when** update submitted, **then** order status updated in DB and customer receives email notification.
- **Given** order in PROCESSING status, **when** status changed to SHIPPED with tracking number, **then** tracking number stored and included in customer notification ("Track your order: <tracking_url>").
- **Given** invalid status transition attempted (e.g., SHIPPED → PENDING), **when** submitted, **then** error message displayed: "Cannot transition from SHIPPED to PENDING".
- **Given** order status updated, **when** update completes, **then** success message displayed: "Order status updated to SHIPPED".
- **Given** order status updated, **when** update fails (DB error), **then** error message displayed and status reverted.
- **Given** admin changes order status, **when** update completes, **then** timeline entry created: "Order status changed from X to Y by [Admin Name]".
- **Given** status change, **when** audit log checked, **then** entry shows: admin user ID, action=ORDER_STATUS_CHANGED, order ID, old status, new status, timestamp.

**Definition of Done:**
- [ ] UpdateOrderStatusUseCase implemented in AdminDashboardService
- [ ] JSF form on order detail page with status dropdown + submit button
- [ ] Status dropdown populated with valid transitions (domain logic validates)
- [ ] Update triggers via AJAX (no full page reload)
- [ ] NotificationPort.sendOrderStatusUpdateEmail() called on success
- [ ] Timeline entry created (via TimelineEventDTO)
- [ ] Audit log entry created (via AuditLogPort)
- [ ] Error handling: invalid transition → ValidationException → FacesMessage
- [ ] Success/error messages clear and user-friendly
- [ ] Update completes in < 1 second

**Story Points:** 8

---

#### S9 — Order Refund Processing

**As an** admin, **I want** to process refunds for orders (approve, reject), **so that** I can handle customer returns and issues.

**Priority:** Must

**Depends on:** S7

**Definition of Ready:**
- [ ] S7 merged
- [ ] ProcessRefundUseCase, ApproveRefundUseCase, RejectRefundUseCase defined
- [ ] PaymentGatewayPort available (for payment reversal)
- [ ] RefundPort defined
- [ ] Refund request workflow confirmed (PENDING → APPROVED → PROCESSED or REJECTED)

**Acceptance Criteria:**

- **Given** order detail page, **when** "Request Refund" button clicked, **then** modal form opens asking: reason for refund (text area), full refund or partial (amount input).
- **Given** refund request, **when** submitted, **then** refund request created with status PENDING and stored.
- **Given** refund request created, **when** user navigates to /admin/refunds, **then** request appears in list with status PENDING.
- **Given** refund request in PENDING status, **when** admin clicks "Approve", **then** payment gateway is called to reverse the charge.
- **Given** payment reversal successful, **when** approved, **then** refund status changes to PROCESSED and customer is notified ("Refund approved, you will see funds within 3-5 business days").
- **Given** payment reversal fails (e.g., payment processor error), **when** approved, **then** error message displayed: "Failed to process refund: [error details]".
- **Given** refund request, **when** admin clicks "Reject", **then** modal opens asking for rejection reason.
- **Given** refund rejected, **when** submitted, **then** refund status changes to REJECTED and customer is notified ("Your refund request was rejected: [reason]").
- **Given** refund processed, **when** order detail viewed, **then** Payment Transactions section shows REFUND transaction with amount and date.

**Definition of Done:**
- [ ] RefundManagementBean (@ViewScoped, @RolesAllowed("ADMIN")) created
- [ ] refunds/list.xhtml page with refund requests table (status, order, customer, amount, actions)
- [ ] refunds/detail.xhtml page with refund details and approve/reject buttons
- [ ] Modal form for refund request (reason text area, full/partial radio, submit button)
- [ ] ListRefundRequestsUseCase implemented
- [ ] ApproveRefundUseCase calls PaymentGatewayPort.processRefund()
- [ ] RejectRefundUseCase stores rejection reason
- [ ] Both send customer notifications (via NotificationPort)
- [ ] Both create audit log entries
- [ ] Timeline entry created on order (refund approved/rejected)
- [ ] Error handling: payment gateway errors caught and displayed
- [ ] Refund status displays as colored badge (PENDING=yellow, APPROVED=blue, PROCESSED=green, REJECTED=red)

**Story Points:** 10

---

### **PRODUCT MANAGEMENT SWIM LANE**

#### S10 — Product List (Admin View, Sales Stats)

**As an** admin, **I want** to see a list of all products with sales statistics (units sold, total revenue, average rating), **so that** I can manage inventory and understand product performance.

**Priority:** Must

**Depends on:** S1, S3

**Definition of Ready:**
- [ ] S3 merged (ProductQueryAdapter working)
- [ ] Product list page design approved
- [ ] Sales stats calculation strategy confirmed

**Acceptance Criteria:**

- **Given** admin navigates to /admin/products, **when** page loads, **then** displays paginated list of all products (20 per page).
- **Given** product list, **when** displayed, **then** columns show: SKU, Name, Category, Price, Cost Price, Stock Quantity, Units Sold, Total Revenue, Avg Rating, Status (Active/Inactive), Actions.
- **Given** product row, **when** viewed, **then** cost price (R$) visible to admin only (not customer-facing).
- **Given** product row, **when** viewed, **then** profit margin calculated: (totalRevenue - costPrice * unitsSold) / totalRevenue, shown as percentage.
- **Given** product list, **when** filtered by category "Electronics", **then** only electronics products displayed.
- **Given** product list, **when** sorted by "Units Sold" DESC, **then** bestsellers appear first.
- **Given** product list, **when** sorted by "Total Revenue" DESC, **then** highest-revenue products appear first.
- **Given** product row, **when** status is INACTIVE, **then** row grayed out and marked "[Inactive]".
- **Given** product row, **when** clicked, **then** navigates to product detail page.
- **Given** product list, **when** price range filter applied (min R$ 50, max R$ 200), **then** only products in price range displayed.

**Definition of Done:**
- [ ] ProductManagementBean (@ViewScoped, @RolesAllowed("ADMIN")) created
- [ ] products/list.xhtml page created with filterable data table
- [ ] ListProductsForAdminUseCase injected and called
- [ ] Filters: search by SKU/name, category dropdown, price range (min/max inputs), active/inactive status radio
- [ ] Sorting on all columns (name, unitsSold, totalRevenue, avgRating, price)
- [ ] Pagination working (20 rows/page)
- [ ] Profit margin calculated and displayed as percentage
- [ ] Cost price displayed (admin-only view, check role)
- [ ] Status badge styled (green for active, gray for inactive)
- [ ] Table loads in < 500ms
- [ ] AJAX-based filtering/sorting (no full page reload)

**Story Points:** 8

---

#### S11 — Create Product (Form, Validation)

**As an** admin, **I want** to create new products in the catalog, **so that** I can add new items for customers to purchase.

**Priority:** Must

**Depends on:** S10

**Definition of Ready:**
- [ ] S10 merged
- [ ] CreateProductUseCase defined
- [ ] Product creation form design approved
- [ ] Validation rules confirmed (name required, SKU unique, price > 0, etc.)

**Acceptance Criteria:**

- **Given** admin clicks "Create Product" button, **when** clicked, **then** navigates to /admin/products/create form page.
- **Given** create product form, **when** displayed, **then** shows fields: SKU (text, must be unique), Name (text), Description (textarea), Category (dropdown multi-select), Price (decimal), Cost Price (decimal), Stock Quantity (integer), Images (file upload multi).
- **Given** form with blank Name field, **when** submitted, **then** validation error displayed: "Product name is required".
- **Given** form with duplicate SKU (already exists in DB), **when** submitted, **then** validation error displayed: "SKU already exists".
- **Given** form with Price = 0, **when** submitted, **then** validation error displayed: "Price must be greater than 0".
- **Given** form with Stock Quantity = -5, **when** submitted, **then** validation error displayed: "Stock quantity must be non-negative".
- **Given** form with all valid data, **when** submitted, **then** product created and saved to DB.
- **Given** product created, **when** creation completes, **then** success message displayed: "Product created successfully" and user redirected to product detail page.
- **Given** form with multiple images uploaded, **when** submitted, **then** all images stored and displayed in product detail.
- **Given** form with invalid image file (e.g., .exe), **when** submitted, **then** validation error: "Only image files (JPG, PNG, GIF) allowed".

**Definition of Done:**
- [ ] CreateProductUseCase implemented in AdminDashboardService
- [ ] products/create.xhtml form page created
- [ ] Form fields: SKU, Name, Description, Category (multi-select), Price, Cost Price, Stock, Images (file upload)
- [ ] Client-side validation (HTML5 + JSF) for required fields, numeric fields, file types
- [ ] Server-side validation: SKU uniqueness, price > 0, stock >= 0, image file types
- [ ] File upload handling (store images in file system or S3)
- [ ] On success: product created, redirect to detail page
- [ ] On error: display FacesMessage, stay on form
- [ ] Form submission via POST (AJAX or regular form submit)
- [ ] CSRF token protection (JSF ViewState)

**Story Points:** 8

---

#### S12 — Edit/Update Product

**As an** admin, **I want** to update existing product details (name, price, category, images), **so that** I can keep catalog current.

**Priority:** Must

**Depends on:** S11

**Definition of Ready:**
- [ ] S11 merged
- [ ] UpdateProductUseCase defined
- [ ] Product edit form design approved

**Acceptance Criteria:**

- **Given** admin on product detail page, **when** "Edit" button clicked, **then** navigates to /admin/products/edit/{id} form page.
- **Given** edit product form, **when** loaded, **then** all fields pre-filled with current product data (SKU read-only, other fields editable).
- **Given** form with changes made (e.g., price changed from 100 to 120), **when** submitted, **then** product updated in DB.
- **Given** product updated, **when** update completes, **then** success message displayed: "Product updated successfully".
- **Given** update attempt with duplicate SKU (different product has same SKU), **when** submitted, **then** error displayed (but own product's SKU allowed).
- **Given** product with existing images, **when** edit form opened, **then** current images displayed with delete option.
- **Given** existing image with delete checkbox, **when** checked and form submitted, **then** image deleted from DB and storage.
- **Given** form with new images uploaded, **when** submitted, **then** new images added alongside existing (or replace if specified).

**Definition of Done:**
- [ ] UpdateProductUseCase implemented
- [ ] products/edit.xhtml form page created
- [ ] Form pre-populated with current product data (via GetProductDetailsUseCase)
- [ ] SKU field read-only (cannot change SKU after creation)
- [ ] All other fields (name, price, cost, stock, category, description) editable
- [ ] Image management: show existing, allow delete, allow add new
- [ ] Validation same as create (price > 0, stock >= 0, etc.)
- [ ] On success: success message, redirect to detail or stay on edit page
- [ ] On error: display error message, stay on form with data intact

**Story Points:** 5

---

#### S13 — Deactivate/Activate Products (Soft-Delete)

**As an** admin, **I want** to deactivate products (hide from customers) without deleting data, **so that** I can manage catalog availability and keep order history intact.

**Priority:** Should

**Depends on:** S12

**Definition of Ready:**
- [ ] S12 merged
- [ ] DeactivateProductUseCase, ActivateProductUseCase defined
- [ ] Soft-delete approach confirmed (set is_active = false, don't hard-delete)

**Acceptance Criteria:**

- **Given** product detail page, **when** "Deactivate" button clicked, **then** confirmation modal shown: "Are you sure you want to deactivate this product? It will no longer be visible to customers".
- **Given** admin confirms deactivation, **when** confirmed, **then** product status set to INACTIVE.
- **Given** deactivated product, **when** customer browses catalog, **then** product not visible (filtered out).
- **Given** deactivated product, **when** customer has link to product detail, **then** shows 404 or "Product no longer available".
- **Given** deactivated product, **when** admin views product list, **then** product shown but marked "[Inactive]" and grayed out.
- **Given** inactive product, **when** admin clicks "Activate", **then** status set back to ACTIVE and product visible to customers again.
- **Given** product deactivated, **when** admin views order history, **then** deactivated product still appears in past orders (data integrity).

**Definition of Done:**
- [ ] DeactivateProductUseCase implemented
- [ ] ActivateProductUseCase implemented
- [ ] Button on product detail page: "Deactivate" (if active) or "Activate" (if inactive)
- [ ] Confirmation modal before deactivation
- [ ] Product status field (is_active boolean) toggled
- [ ] Audit log entry created: "Product [ID] deactivated/activated by [Admin]"
- [ ] Catalog query filters out inactive products (for customer view)
- [ ] Admin view includes inactive products (with badge)
- [ ] Order history preserved (old orders still show product even if later deactivated)

**Story Points:** 3

---

### **CUSTOMER MANAGEMENT SWIM LANE**

#### S14 — Customer List with LTV & Order History

**As an** admin, **I want** to view all customers with lifetime value (LTV) and order counts, **so that** I can identify high-value customers and understand customer base.

**Priority:** Should

**Depends on:** S1, S3

**Definition of Ready:**
- [ ] S3 merged (UserQueryAdapter working)
- [ ] Customer list page design approved
- [ ] LTV calculation strategy confirmed

**Acceptance Criteria:**

- **Given** admin navigates to /admin/customers, **when** page loads, **then** displays paginated list of all customers (20 per page).
- **Given** customer list, **when** displayed, **then** columns show: Email, Full Name, Total Orders, Lifetime Value (LTV), Average Order Value, Last Login, Status (Active/Blocked), Actions.
- **Given** customer list, **when** sorted by "Lifetime Value" DESC, **then** highest-value customers appear first.
- **Given** customer row, **when** viewed, **then** LTV calculated as SUM(order.total) for all orders by that customer.
- **Given** customer list, **when** filtered by status=Active, **then** only active, non-blocked customers displayed.
- **Given** customer list, **when** searched by "john@example.com", **then** only customers matching email displayed.
- **Given** customer row with status=Blocked, **then** row highlighted in red and status shows "[Blocked]".
- **Given** customer row, **when** clicked, **then** navigates to customer detail page.

**Definition of Done:**
- [ ] CustomerManagementBean (@ViewScoped, @RolesAllowed("ADMIN")) created
- [ ] customers/list.xhtml page created
- [ ] ListUsersUseCase injected (filters for CUSTOMER role only)
- [ ] Columns: email, fullName, totalOrders, lifetimeValue, averageOrderValue, lastLoginAt, status
- [ ] LTV calculated in query (SUM of order totals)
- [ ] Filters: search by email/name, status (active/blocked), isActive (active/inactive)
- [ ] Sorting on all columns
- [ ] Pagination (20 rows/page)
- [ ] Status badge styled (green for active, red for blocked, gray for inactive)
- [ ] Table loads in < 500ms

**Story Points:** 8

---

#### S15 — Customer Detail View

**As an** admin, **I want** to see complete customer profile including contact info, addresses, order history, and preferences, **so that** I can understand customer context and resolve issues.

**Priority:** Should

**Depends on:** S14

**Definition of Ready:**
- [ ] S14 merged
- [ ] Customer detail page design approved
- [ ] GetUserDetailsUseCase defined

**Acceptance Criteria:**

- **Given** customer list, **when** customer row clicked, **then** navigates to /admin/customers/{id} detail page.
- **Given** customer detail page, **when** loaded, **then** displays: email, full name, phone, account created date, last login date, status (active/blocked/inactive), LTV, total orders.
- **Given** customer detail, **when** viewed, **then** shows "Addresses" section with all addresses: street, city, state, postal code, label (Home/Work), default flag.
- **Given** customer detail, **when** viewed, **then** shows "Recent Orders" (last 5 orders) with order #, date, total, status.
- **Given** recent order in list, **when** clicked, **then** navigates to order detail page.
- **Given** customer detail, **when** viewed, **then** shows "Preferences": preferred language, notifications enabled/disabled.
- **Given** customer detail, **when** viewed, **then** shows "Account Actions" button: Block, Send Support Email, View Audit Log.

**Definition of Done:**
- [ ] CustomerManagementBean extended with getCustomerDetails(Long userId)
- [ ] customers/detail.xhtml page created
- [ ] Sections: account info, contact info, addresses list, recent orders list, preferences, account actions
- [ ] GetUserDetailsUseCase calls UserQueryPort.findUserDetails()
- [ ] CustomerAdminDTO includes: email, fullName, phone, createdAt, lastLoginAt, status, lifetimeValue, totalOrders, recentOrders (List<OrderAdminDTO>), addresses (List<AddressDTO>), preferences
- [ ] Recent orders table with links to order detail
- [ ] Addresses displayed as list (street, city, state, postal, label, default badge)
- [ ] Responsive design

**Story Points:** 5

---

#### S16 — Block/Unblock Customer Accounts

**As an** admin, **I want** to block suspicious or problematic customers (prevent login, restrict checkout), **so that** I can manage risk and fraud.

**Priority:** Should

**Depends on:** S15

**Definition of Ready:**
- [x] S15 merged
- [x] BlockUserUseCase, UnblockUserUseCase defined
- [x] Block reason tracking strategy confirmed (deferred — see scope notes)

**Acceptance Criteria:**

- **Given** customer detail page, **when** account status is Active, **then** "Block Account" button visible.
- **Given** blocked customer, **when** they try to log in, **then** login fails: "Your account has been blocked. Contact support for details".
- **Given** blocked customer account, **when** admin views detail page, **then** account status shows "[Blocked]" and "Unblock Account" button visible.
- **Given** "Unblock Account" button clicked, **when** clicked, **then** confirmation modal shown, and on confirm account status set back to Active.
- **Given** customer blocked/unblocked, **when** audit log checked, **then** entry shows: "Customer [ID] blocked by [Admin]" or "...unblocked".

**Definition of Done:**
- [x] BlockUserUseCase, UnblockUserUseCase implemented in AdminDashboardService
- [x] Buttons on customer detail page: "Block Account" (if active) or "Unblock Account" (if blocked)
- [x] Confirmation modal before block/unblock
- [x] User status field updated to ACTIVE/INACTIVE (block = INACTIVE, per ChangeUserStatusUseCase)
- [x] Login check: canLogin() rejects INACTIVE/LOCKED accounts
- [ ] Checkout check: if customer status=BLOCKED, show error and prevent checkout (deferred)
- [x] Audit log entries created (USER_BLOCKED/USER_UNBLOCKED)
- [x] Customer status badge styled (red for blocked)

**Story Points:** 5

**Scope notes / debt (accepted, fast scope):**
- Block/unblock is implemented **without a reason**: `ChangeUserStatusUseCase` only takes a userId, so no reason is stored or shown. The block-reason modal (dropdown + optional text), storing the reason on the user record, and the audit message including `reason: [reason]` were **deferred** to a follow-up story.
- Checkout is not blocked for suspended customers yet — `order-checkout` does not inspect customer status. Deferred; link from S20+ when checkout enforces account state.
- "Blocked" is represented as `UserStatus.INACTIVE` (via `deactivate`); the `BLOCKED` literal status was not introduced.

---

### **REFUND MANAGEMENT SWIM LANE**

#### S17 — Refund Requests List

**As an** admin, **I want** to view all refund requests (pending, approved, rejected, processed), **so that** I can manage and track refunds.

**Priority:** Should

**Depends on:** S1, S3

**Definition of Ready:**
- [x] S3 merged (refund queries working)
- [x] Refund list page design approved
- [x] Refund request status flow confirmed (PENDING → APPROVED → PROCESSED or REJECTED)

**Acceptance Criteria:**

- **Given** admin navigates to /admin/refunds, **when** page loads, **then** displays paginated list of all refund requests.
- **Given** refund list, **when** displayed, **then** columns show: Order #, Customer Name, Refund Amount, Status, Reason, Requested Date, Actions.
- **Given** refund list, **when** filtered by status=PENDING, **then** only pending refunds displayed (awaiting approval).
- **Given** refund status column, **when** viewed, **then** status displayed as colored badge (PENDING=yellow, APPROVED=blue, PROCESSED=green, REJECTED=red).
- **Given** refund row, **when** clicked, **then** navigates to refund detail page.
- **Given** list, **when** sorted by "Requested Date" DESC, **then** newest refunds appear first.
- **Given** 50 refunds, **when** page 2 viewed, **then** correct pagination applied.

**Definition of Done:**
- [x] RefundManagementBean (@ViewScoped, @RolesAllowed("ADMIN")) created
- [x] refunds/list.xhtml page created
- [x] RefundManagementUseCase.listRefundRequests injected and called
- [x] Filters: status dropdown (date range picker deferred — see implementation notes)
- [ ] Sorting: Requested Date, Refund Amount, Status (deferred)
- [x] Pagination (20 rows/page)
- [x] Status badges colored
- [x] Link/row navigation to detail page
- [ ] Table loads in < 500ms (not measured)

**Implementation notes:** Refund list lives at `/admin-dashboard/refunds/list.xhtml` and is reachable from the dashboard. Navigation to the detail page is a "View Details" link per row rather than a full row click. The Customer Name column is not shown (the refund domain model has no customer reference; the order link covers it). No date range picker or sorting yet.

**Story Points:** 5

---

#### S18 — Approve Refund (Payment Reversal)

**As an** admin, **I want** to approve a refund request and process the payment reversal, **so that** customers receive their money back.

**Priority:** Should

**Depends on:** S17, S9

**Definition of Ready:**
- [x] S17 merged
- [x] ApproveRefundUseCase defined (RefundManagementUseCase.approveRefund)
- [x] PaymentGatewayPort available (processRefund method) — mock adapter
- [x] Payment reversal strategy confirmed — reverse via `processRefund` in the same transaction

**Acceptance Criteria:**

- **Given** refund detail page for a PENDING refund, **when** viewed, **then** displays: order #, customer name, refund amount, reason, requested date, and "Approve" button.
- **Given** "Approve" button clicked, **when** clicked, **then** confirmation modal shown: "Approve refund of R$ 100.00 to customer john@example.com? This will reverse the charge at the payment processor".
- **Given** admin confirms approval, **when** confirmed, **then** system calls PaymentGatewayPort.processRefund() to reverse charge.
- **Given** payment reversal successful, **when** approved, **then** refund status updated to PROCESSED and customer receives notification email: "Your refund has been approved. You will see funds within 3-5 business days".
- **Given** payment reversal fails (payment processor error), **when** approved, **then** error message displayed: "Failed to process refund: [error details from payment gateway]" and refund status remains PENDING.
- **Given** refund approved, **when** order detail viewed, **then** Payment Transactions section shows new REFUND transaction (type=REFUND, status=SUCCESS, amount, date).
- **Given** refund approved, **when** audit log checked, **then** entry shows: "Refund [ID] approved by [Admin]".

**Definition of Done:**
- [x] ApproveRefundUseCase implemented (RefundApplicationService.approveRefund)
- [x] refunds/detail.xhtml page with "Approve" button and confirmation (browser confirm dialog, consistent with S16)
- [x] Confirmation asks for confirmation (yes/no)
- [x] On confirm: calls PaymentGatewayPort.processRefund() (mock adapter)
- [x] On success: refund status = PROCESSED and order → REFUNDED; customer notification email sent (via NotificationPort) and REFUND_PROCESSED audit event published — notification/audit added in the S18/S19 follow-up
- [x] On failure: `PaymentFailedException` shown, transaction rolls back, request stays PENDING
- [x] Refund status badge updated after the action (PENDING → PROCESSED on reload)
- [x] Success/error messages clear

**Implementation notes:** Approving a refund reverses the charge through `PaymentGatewayPort.processRefund` (mock adapter) and marks the request PROCESSED in the same transaction; on reversal failure the transaction rolls back and the request stays PENDING (per the acceptance criteria). The customer notification email (`notifyRefundApproved`) and the `REFUND_PROCESSED` audit-log entry were wired in the follow-up commit. Order-timeline/payment-transactions UI section still not implemented. The confirmation dialog is the browser-native `confirm()` used for block/unblock in S16, not the custom `confirm-modal` component.

**Story Points:** 8

---

#### S19 — Reject Refund

**As an** admin, **I want** to reject a refund request (with reason), **so that** I can handle policy violations or disputes.

**Priority:** Should

**Depends on:** S18

**Definition of Ready:**
- [x] S18 merged
- [x] RejectRefundUseCase defined (RefundManagementUseCase.rejectRefund)

**Acceptance Criteria:**

- **Given** refund detail page for a PENDING refund, **when** viewed, **then** displays: "Reject" button alongside "Approve".
- **Given** "Reject" button clicked, **when** clicked, **then** modal form opens asking: rejection reason (dropdown: Policy violation, Customer request, Insufficient evidence, Other; optional text area).
- **Given** admin selects reason and submits, **when** submitted, **then** refund status updated to REJECTED.
- **Given** refund rejected, **when** complete, **then** customer receives notification email: "Your refund request has been rejected. Reason: [reason]".
- **Given** refund rejected, **when** audit log checked, **then** entry shows: "Refund [ID] rejected by [Admin] reason: [reason]".

**Definition of Done:**
- [x] RejectRefundUseCase implemented (RefundApplicationService.rejectRefund)
- [x] Reject button on refund detail page
- [ ] Modal form with reason dropdown + optional text — implemented as a free-text textarea (see notes)
- [x] On submit: refund status = REJECTED; customer notification email sent (via NotificationPort) and REFUND_REJECTED audit event published — notification/audit added in the S18/S19 follow-up
- [x] No payment reversal (only approval processes refunds)
- [x] Refund status badge updated (PENDING → REJECTED, red colored)

**Implementation notes:** The rejection reason is a free-text textarea (blank reasons are rejected with a validation message). The dropdown of policy-violation reasons is deferred. The customer notification email (`notifyRefundRejected`) and the `REFUND_REJECTED` audit-log entry were wired in the follow-up commit. The order returns to DELIVERED on rejection.

**Story Points:** 3

---

### **REPORTING & ANALYTICS SWIM LANE**

#### S20 — Revenue Report (Date Range, Breakdown)

**As an** admin, **I want** to generate revenue reports with breakdown by category, payment method, time period, **so that** I can analyze business performance.

**Priority:** Should

**Depends on:** S1, S3

**Definition of Ready:**
- [ ] S3 merged (aggregation queries working)
- [ ] RevenueReportDTO defined
- [ ] Report generation strategy confirmed

**Acceptance Criteria:**

- **Given** admin navigates to /admin/reports, **when** page loads, **then** displays report selection page with links: "Revenue Report", "Product Report", "Customer Report".
- **Given** admin clicks "Revenue Report", **when** clicked, **then** navigates to revenue report page with filters: From Date (date picker), To Date (date picker), Group By (dropdown: Daily, Weekly, Monthly).
- **Given** report page, **when** date range selected (e.g., 2026-07-01 to 2026-07-31), **then** "Generate" button clicked and report generated.
- **Given** revenue report generated, **when** displayed, **then** shows: Total Revenue, Total Tax, Total Shipping, Net Revenue, Total Orders, Average Order Value (all calculated for date range).
- **Given** report, **when** viewed, **then** shows line chart: revenue by date (x-axis: dates, y-axis: revenue amount).
- **Given** report, **when** viewed, **then** shows breakdown tables: Revenue by Category (category name, revenue), Revenue by Payment Method (method, count, revenue).
- **Given** report with data, **when** "Export" button clicked, **then** dialog shown asking: PDF or CSV format.

**Definition of Done:**
- [ ] ReportGenerationBean (@ViewScoped, @RolesAllowed("ADMIN")) created
- [ ] reports/index.xhtml with report selection links
- [ ] reports/revenue.xhtml with date range filters and generated report
- [ ] GenerateRevenueReportUseCase implemented
- [ ] Queries: SUM(order.total), SUM(order.tax), SUM(order.shipping), COUNT(orders), breakdown by category/payment method
- [ ] Charts: line chart revenue over time (PrimeFaces <p:chart>)
- [ ] Tables: category breakdown, payment method breakdown
- [ ] Export buttons (PDF, CSV)
- [ ] Report loads in < 3 seconds (complex queries)

**Story Points:** 8

---

#### S21 — Product Performance Report

**As an** admin, **I want** to see product performance metrics (top sellers, revenue, profit margin), **so that** I can identify bestsellers and underperformers.

**Priority:** Should

**Depends on:** S3

**Definition of Ready:**
- [ ] S3 merged
- [ ] ProductReportDTO defined
- [ ] Report page design approved

**Acceptance Criteria:**

- **Given** admin navigates to /admin/reports/products, **when** page loads, **then** displays: "Top 10 Sellers", "Top 10 by Revenue", "Bottom 10 by Sales" tables.
- **Given** "Top 10 Sellers" table, **when** displayed, **then** columns show: SKU, Product Name, Units Sold, Total Revenue, Profit Margin (%).
- **Given** product in bottom performers, **when** viewed, **then** highlighted in yellow/red as warning.
- **Given** report, **when** filtered by category "Electronics", **then** only electronics products included in all tables.
- **Given** report data, **when** viewed, **then** bar chart shows "Units Sold by Category" (x-axis: categories, y-axis: units).

**Definition of Done:**
- [ ] reports/products.xhtml created
- [ ] GenerateProductReportUseCase implemented
- [ ] Tables: top 10 sellers, top 10 by revenue, bottom 10
- [ ] Profit margin calculated: (totalRevenue - costPrice * unitsSold) / totalRevenue
- [ ] Bar chart: units sold by category
- [ ] Category filter (optional, all categories default)
- [ ] Report loads in < 2 seconds

**Story Points:** 5

---

#### S22 — Customer Insights Report

**As an** admin, **I want** to understand customer base (new customers, repeat rate, churn), **so that** I can track customer trends.

**Priority:** Should

**Depends on:** S3

**Definition of Ready:**
- [ ] S3 merged
- [ ] CustomerReportDTO defined

**Acceptance Criteria:**

- **Given** admin navigates to /admin/reports/customers, **when** page loads, **then** displays metrics: Total Customers, New Customers (this period), Repeat Customer Rate (%), Average LTV, Churn Rate (%).
- **Given** report, **when** viewed, **then** shows line chart: "New Customers by Date" (x-axis: dates, y-axis: count).
- **Given** repeat customer rate, **when** displayed, **then** calculated as: (customers with > 1 order) / total customers * 100.
- **Given** churn rate, **when** displayed, **then** calculated as: customers inactive for 90+ days / total customers * 100.
- **Given** report, **when** date range filter applied, **then** metrics recalculated for that period.

**Definition of Done:**
- [ ] reports/customers.xhtml created
- [ ] GenerateCustomerReportUseCase implemented
- [ ] Metrics: total customers, new customers, repeat rate, average LTV, churn rate
- [ ] Line chart: new customers over time
- [ ] Date range filters
- [ ] Report loads in < 2 seconds

**Story Points:** 5

---

#### S23 — Report Export (PDF, CSV)

**As an** admin, **I want** to export reports in PDF or CSV format, **so that** I can share with stakeholders or analyze in Excel.

**Priority:** Should

**Depends on:** S20, S21, S22

**Definition of Ready:**
- [ ] S20, S21, S22 merged (reports working)
- [ ] ReportPort (PDF, CSV) defined
- [ ] PDF/CSV generation tools selected (iText, Apache POI, Freemarker templates)

**Acceptance Criteria:**

- **Given** revenue report, **when** "Export" button clicked, **then** dialog opens: PDF or CSV radio selection.
- **Given** admin selects "PDF", **when** submitted, **then** PDF file downloaded (filename: "revenue-report-2026-07-01-2026-07-31.pdf").
- **Given** PDF report, **when** opened, **then** includes: title, date range, KPI cards (Total Revenue, Tax, Shipping, Net), tables (category breakdown, payment method breakdown), charts embedded.
- **Given** admin selects "CSV", **when** submitted, **then** CSV file downloaded with columns: Date, Category, Payment Method, Orders, Revenue, Tax, Shipping.
- **Given** CSV file, **when** opened in Excel, **then** data properly formatted (numbers right-aligned, dates formatted).
- **Given** product report, **when** exported as PDF, **then** includes: top sellers table, bottom performers table, bar charts.
- **Given** customer report, **when** exported as CSV, **then** includes: Date, New Customers, Repeat Rate, Average LTV, Churn Rate.

**Definition of Done:**
- [ ] Export buttons on all report pages (S20, S21, S22)
- [ ] Export dialog with PDF/CSV options
- [ ] ReportGeneratorAdapter.generatePDF() and generateCSV() implemented
- [ ] PDF generation using Flying Saucer or iText (HTML → PDF)
- [ ] CSV generation using Apache Commons CSV or OpenCSV
- [ ] Freemarker templates for report layouts (PDF, CSV)
- [ ] Downloaded file names include date range
- [ ] PDF styled with logo, colors, nice formatting
- [ ] CSV headers clear and Excel-friendly
- [ ] Export completes in < 5 seconds

**Story Points:** 8

---

### **MONITORING & SECURITY SWIM LANE**

#### S24 — Audit Log Viewer

**As an** admin, **I want** to view audit logs of all admin actions (who, what, when), **so that** I can track changes and detect suspicious activity.

**Priority:** Should

**Depends on:** S1, S3

**Definition of Ready:**
- [ ] S3 merged (audit query adapter working)
- [ ] AuditLogDTO defined
- [ ] Audit log page design approved

**Acceptance Criteria:**

- **Given** admin navigates to /admin/audit-log, **when** page loads, **then** displays paginated audit log entries (20 per page, newest first).
- **Given** audit log table, **when** displayed, **then** columns show: Admin Name, Action Type (ORDER_STATUS_CHANGED, PRODUCT_CREATED, REFUND_APPROVED, etc.), Entity Type (ORDER, PRODUCT, REFUND), Entity ID, Description, Date/Time, IP Address.
- **Given** audit log, **when** filtered by admin user "john@example.com", **then** only entries by that admin displayed.
- **Given** audit log, **when** filtered by action type "REFUND_APPROVED", **then** only refund approvals shown.
- **Given** audit log, **when** date range filter applied, **then** only entries in that range displayed.
- **Given** audit log entry row, **when** description is long, **then** truncated with "..." and expandable tooltip shows full text.
- **Given** admin searches for "Order #123", **when** search submitted, **then** entries related to that order shown.

**Definition of Done:**
- [ ] AuditLogBean (@ViewScoped, @RolesAllowed("ADMIN")) created
- [ ] audit-log/view.xhtml page created
- [ ] ViewAuditLogUseCase injected
- [ ] AuditQueryAdapter queries audit log table
- [ ] Filters: admin user dropdown, action type dropdown, date range, entity type, entity ID search
- [ ] Sorting: Date DESC (newest first)
- [ ] Pagination (20 rows/page)
- [ ] Tooltip on description (show full text)
- [ ] Table loads in < 1 second (indexes on user_id, created_at)

**Story Points:** 5

---

#### S25 — Admin User Management (Create/List/Roles)

**As a** super-admin, **I want** to create and manage admin user accounts and their roles, **so that** I can control access to admin features.

**Priority:** Should

**Depends on:** S1

**Definition of Ready:**
- [ ] S1 merged
- [ ] CreateAdminUserUseCase, ListAdminUsersUseCase defined
- [ ] Admin roles (ADMIN, SUPER_ADMIN) defined
- [ ] Admin user management page design approved

**Acceptance Criteria:**

- **Given** super-admin navigates to /admin/settings/admin-users, **when** page loads, **then** displays list of all admin users.
- **Given** admin user list, **when** displayed, **then** columns show: Email, Full Name, Roles (comma-separated), Last Login, Created Date, Actions (Edit, Delete).
- **Given** super-admin clicks "Create Admin User", **when** clicked, **then** navigates to form page.
- **Given** create admin form, **when** displayed, **then** shows fields: Email (text), Password (password), Confirm Password, Full Name, Roles (checkboxes: ADMIN, SUPER_ADMIN).
- **Given** form with valid data, **when** submitted, **then** admin user created with hashed password.
- **Given** admin user created, **when** creation completes, **then** success message shown and user added to list.
- **Given** non-super-admin user, **when** tries to access admin user management, **then** access denied (403 Forbidden).

**Definition of Done:**
- [ ] AdminUserManagementBean (@ViewScoped, @RolesAllowed("SUPER_ADMIN")) created
- [ ] admin/settings/admin-users.xhtml list page
- [ ] admin/settings/admin-users-create.xhtml form page
- [ ] CreateAdminUserUseCase implemented (hashes password via PasswordHasherPort)
- [ ] ListAdminUsersUseCase implemented (queries admin users)
- [ ] Form validation: email unique, password >= 8 chars, roles selected
- [ ] Roles displayed as badges
- [ ] Access check: only SUPER_ADMIN can access

**Story Points:** 8

---

#### S26 — RBAC Enforcement (@RolesAllowed, Authorization Checks)

**As** the system, **I want** to enforce role-based access control on all admin features, **so that** non-admin users cannot access restricted pages.

**Priority:** Must

**Depends on:** All stories

**Definition of Ready:**
- [ ] All admin beans created
- [ ] @RolesAllowed("ADMIN") defined
- [ ] Container-level authorization configured (Open Liberty)

**Acceptance Criteria:**

- **Given** a non-admin user, **when** they try to access /admin/dashboard, **then** request is rejected with 403 Forbidden (handled by servlet container).
- **Given** a CUSTOMER user, **when** they attempt to access OrderManagementBean method, **then** @RolesAllowed("ADMIN") enforces deny.
- **Given** a JSF page with admin content, **when** non-admin tries direct URL access, **then** page not accessible (interceptor or filter redirects to login).
- **Given** admin buttons on order detail (e.g., "Approve Refund"), **when** rendered, **then** only visible to ADMIN users (JSF rendered attribute).
- **Given** admin user with ADMIN role, **when** accessing admin pages, **then** full access granted.
- **Given** super-admin user, **when** accessing any admin feature, **then** full access granted.

**Definition of Done:**
- [ ] All admin beans decorated with @RolesAllowed("ADMIN") or @RolesAllowed("SUPER_ADMIN")
- [ ] All admin use case methods check role via @RolesAllowed
- [ ] JSF pages with sensitive buttons use rendered="#{currentUser.hasRole('ADMIN')}"
- [ ] Unauthorized requests result in 403 (servlet container enforces)
- [ ] Admin URL paths protected (/admin/*)
- [ ] Session check: if user not logged in, redirect to login before role check
- [ ] Audit log entry created if unauthorized access attempt detected

**Story Points:** 5

---

#### S27 — ArchUnit Admin Boundary Tests

**As** the system, **I want** to verify hexagonal boundaries for Admin Dashboard module via ArchUnit tests, **so that** architecture rules are enforced automatically.

**Priority:** Should

**Depends on:** All stories

**Definition of Ready:**
- [ ] All admin code implemented
- [ ] ArchUnit library available (already in project)
- [ ] Test class structure planned

**Acceptance Criteria:**

- **Given** AdminHexagonalArchitectureTest, **when** run, **then** verifies: admin application layer has zero adapter imports.
- **Given** test, **when** run, **then** verifies: admin adapters don't import each other (OrderQueryAdapter doesn't import ProductQueryAdapter).
- **Given** test, **when** run, **then** verifies: all use-case interfaces in application.port.in packages.
- **Given** test, **when** run, **then** verifies: all port interfaces in application.port.out packages.
- **Given** test, **when** run, **then** verifies: all DTOs in application.dto packages.
- **Given** test, **when** run, **then** verifies: admin beans (@ViewScoped, @RequestScoped) reside in web/jsf/beans packages.
- **Given** test run, **when** all rules pass, **then** build succeeds.

**Definition of Done:**
- [ ] AdminHexagonalArchitectureTest class created
- [ ] 6+ ArchUnit test methods covering above rules
- [ ] Tests run as part of Maven build (test phase)
- [ ] All tests pass
- [ ] Tests documented (what they verify, why)

**Story Points:** 3

---

## Backlog Summary Table

| # | Story | Depends on | Priority | Points |
|---|---|---|---|---|
| S1 | Create admin query adapters & DTOs | — | Must | 8 |
| S2 | Create complex aggregation queries | S1 | Must | 8 |
| S3 | Order/product/user query adapters | S1, S2 | Must | 8 |
| S4 | Dashboard home with KPI metrics | S2 | Must | 5 |
| S5 | Dashboard charts | S4 | Should | 5 |
| S6 | Order list with filters | S3 | Must | 8 |
| S7 | Order detail view | S6 | Must | 8 |
| S8 | Update order status + notification | S7 | Must | 8 |
| S9 | Order refund processing | S7 | Must | 10 |
| S10 | Product list (admin view) | S3 | Must | 8 |
| S11 | Create product | S10 | Must | 8 |
| S12 | Edit/update product | S11 | Must | 5 |
| S13 | Deactivate/activate products | S12 | Should | 3 |
| S14 | Customer list with LTV | S3 | Should | 8 |
| S15 | Customer detail view | S14 | Should | 5 |
| S16 | Block/unblock customers | S15 | Should | 5 |
| S17 | Refund requests list | S3 | Should | 5 |
| S18 | Approve refund | S17 | Should | 8 |
| S19 | Reject refund | S18 | Should | 3 |
| S20 | Revenue report | S3 | Should | 8 |
| S21 | Product performance report | S3 | Should | 5 |
| S22 | Customer insights report | S3 | Should | 5 |
| S23 | Report export (PDF, CSV) | S20, S21, S22 | Should | 8 |
| S24 | Audit log viewer | S3 | Should | 5 |
| S25 | Admin user management | S1 | Should | 8 |
| S26 | RBAC enforcement | All | Must | 5 |
| S27 | ArchUnit boundary tests | All | Should | 3 |

**Total:** 165 points

**Sequencing:**
- **Foundation (S1–S3):** 24 points, 2 weeks. Adapters + DTOs + queries ready, but no UI yet.
- **Dashboard Home (S4–S5):** 10 points, 1 week. First admin UI, immediate value.
- **Order Management (S6–S9):** 34 points, 2.5 weeks. Core operational feature.
- **Product Management (S10–S13):** 24 points, 2 weeks. Catalog control.
- **Customer Management (S14–S16):** 18 points, 1.5 weeks. Customer admin.
- **Refund Management (S17–S19):** 16 points, 1.5 weeks. Financial ops.
- **Reporting (S20–S23):** 26 points, 2 weeks. Business intelligence.
- **Monitoring & Security (S24–S27):** 21 points, 1.5 weeks. Audit, access control, testing.

**Parallel Work:** S4–S5, S6–S9, S10–S13, S14–S16, S17–S19 can proceed in parallel after their dependencies are met.

**MVP Subset (Minimum Viable Product):** S1–S9, S26 = 75 points = 5–6 weeks. Delivers dashboard + order management + RBAC.

**Full Release:** All 27 stories = 165 points = 10–12 weeks for solo developer.
