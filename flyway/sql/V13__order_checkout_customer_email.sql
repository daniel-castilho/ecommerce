-- S6: persist the customer email on the Order aggregate so notifications can
-- address the customer. Column is nullable for legacy rows; new orders set it.
-- NOTE: must be registered manually in flyway_schema_history (no runner; see V5-V12).

ALTER TABLE tb_order ADD COLUMN customer_email VARCHAR(255);
