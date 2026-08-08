-- Cost price on the product catalog (S10/S21 debt). NULL until an admin enters
-- it; used by the admin product list and the product performance report to
-- compute the gross profit margin (revenue - costPrice * unitsSold) / revenue.
-- Admin-only data, never exposed to the customer-facing catalog.
-- NOTE: must be registered manually in flyway_schema_history (no runner; see V5-V18).

ALTER TABLE tb_product ADD COLUMN cost_price NUMERIC(19,2);
