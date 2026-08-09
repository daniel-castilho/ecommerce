package com.loja.promotions.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.PostgreSQLContainer;

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
