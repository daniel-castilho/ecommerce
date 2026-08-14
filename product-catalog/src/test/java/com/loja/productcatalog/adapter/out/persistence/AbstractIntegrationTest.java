package com.loja.productcatalog.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

abstract class AbstractIntegrationTest {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("shop")
            .withUsername("user")
            .withPassword("password");

    static EntityManagerFactory emf;
    EntityManager em;

    @BeforeAll
    static void startContainer() {
        postgres.start();
        Map<String, String> props = new HashMap<>();
        props.put("jakarta.persistence.jdbc.url", postgres.getJdbcUrl());
        props.put("jakarta.persistence.jdbc.user", postgres.getUsername());
        props.put("jakarta.persistence.jdbc.password", postgres.getPassword());
        emf = Persistence.createEntityManagerFactory("ecommercePU", props);
        applyFtsSchema();
    }

    /**
     * Applies the weighted STORED tsvector column + GIN index from Flyway V31 after
     * Hibernate's {@code drop-and-create}, because a {@code GENERATED ALWAYS ... STORED}
     * {@code tsvector} column cannot be declared by a JPA entity mapping. Must stay in
     * sync with {@code flyway/sql/V31__product_fts_weighted_stored.sql} (weights A/B).
     */
    private static void applyFtsSchema() {
        try (Connection connection = DriverManager.getConnection(
                     postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE tb_product "
                    + "ADD COLUMN IF NOT EXISTS search_vector tsvector GENERATED ALWAYS AS ("
                    + "setweight(to_tsvector('english', coalesce(name, '')), 'A') || "
                    + "setweight(to_tsvector('english', coalesce(sku, '')), 'A') || "
                    + "setweight(to_tsvector('english', coalesce(short_description, '')), 'B')"
                    + ") STORED");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_tb_product_search_vector "
                    + "ON tb_product USING GIN (search_vector)");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to apply V31 FTS schema to test Postgres", e);
        }
    }

    @AfterAll
    static void stopContainer() {
        if (emf != null) emf.close();
        if (postgres != null) postgres.stop();
    }

    void setUpEntityManager() {
        em = emf.createEntityManager();
    }
}
