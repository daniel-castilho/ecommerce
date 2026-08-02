-- S11: optimistic locking on tb_order.
-- Adds the version column used by @Version (Hibernate) to detect concurrent
-- updates and translate them into a friendly "please reload" message.
-- NOTE: must be registered manually in flyway_schema_history (no runner; see V5/V6/V7).

ALTER TABLE tb_order ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
