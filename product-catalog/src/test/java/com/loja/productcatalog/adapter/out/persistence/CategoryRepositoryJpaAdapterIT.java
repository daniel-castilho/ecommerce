package com.loja.productcatalog.adapter.out.persistence;

import com.loja.productcatalog.application.service.CategoryTreeCache;
import com.loja.productcatalog.domain.model.Category;
import com.loja.productcatalog.domain.model.Slug;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryRepositoryJpaAdapterIT extends AbstractIntegrationTest {

    private CategoryRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        setUpEntityManager();
        adapter = new CategoryRepositoryAdapter();
        adapter.em = em;
        adapter.cache = new CategoryTreeCache();
        em.getTransaction().begin();
        em.createNativeQuery("TRUNCATE TABLE tb_product_category, tb_category RESTART IDENTITY CASCADE")
                .executeUpdate();
        em.getTransaction().commit();
        em.clear();
    }

    @AfterEach
    void tearDown() {
        if (em != null && em.isOpen()) {
            em.close();
        }
    }

    private <T> T inTx(Supplier<T> operation) {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        try {
            T result = operation.get();
            tx.commit();
            return result;
        } catch (RuntimeException | Error e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            em.clear();
        }
    }

    @Test
    void shouldPersistRootAndAssignId() {
        Category saved = save(category(null, "Eletronicos", "eletronicos", null, true));

        assertThat(saved.getId()).isNotNull();
        assertThat(inTx(() -> adapter.findById(saved.getId()))).isPresent()
                .get().extracting(Category::getName).isEqualTo("Eletronicos");
    }

    @Test
    void shouldBuildNestedTreeInOneCall() {
        Category electronics = save(category(null, "Eletronicos", "eletronicos", null, true));
        Category phones = save(category(null, "Celulares", "celulares", electronics, true));
        Category accessories = save(category(null, "Acessorios", "acessorios", phones, true));

        List<Category> tree = inTx(() -> adapter.findAll());

        assertThat(tree).hasSize(1);
        Category root = tree.get(0);
        assertThat(root.getName()).isEqualTo("Eletronicos");
        assertThat(root.getChildren()).extracting(Category::getName).containsExactly("Celulares");
        Category phonesNode = root.getChildren().get(0);
        assertThat(phonesNode.getChildren()).extracting(Category::getName).containsExactly("Acessorios");
        assertThat(phonesNode.getParent().getName()).isEqualTo("Eletronicos");
    }

    @Test
    void shouldServeTreeFromCacheAndInvalidateOnMutation() {
        save(category(null, "Eletronicos", "eletronicos", null, true));

        List<Category> first = inTx(() -> adapter.findAll());
        assertThat(first).hasSize(1);

        save(category(null, "Livros", "livros", null, true));
        List<Category> second = inTx(() -> adapter.findAll());
        assertThat(second).hasSize(2);

        inTx(() -> {
            adapter.delete(first.get(0).getId());
            return null;
        });
        List<Category> third = inTx(() -> adapter.findAll());
        assertThat(third).extracting(Category::getName).containsExactly("Livros");
    }

    @Test
    void shouldExcludeInactiveFromPublicTreeButResolveDirectly() {
        Category active = save(category(null, "Eletronicos", "eletronicos", null, true));
        Category inactive = save(category(null, "Desativada", "desativada", null, false));

        List<Category> publicTree = inTx(() -> adapter.findAllActive());
        assertThat(publicTree).extracting(Category::getName).containsExactly("Eletronicos");

        assertThat(inTx(() -> adapter.findById(inactive.getId()))).isPresent();
        assertThat(inTx(() -> adapter.existsById(active.getId()))).isTrue();
        assertThat(inTx(() -> adapter.existsById(999L))).isFalse();
    }

    @Test
    void shouldFindBySlug() {
        Category saved = save(category(null, "Esporte", "esporte", null, true));

        assertThat(inTx(() -> adapter.findBySlug(new Slug("esporte")))).isPresent()
                .get().extracting(Category::getId).isEqualTo(saved.getId());
        assertThat(inTx(() -> adapter.findBySlug(new Slug("nao-existe")))).isEmpty();
    }

    @Test
    void shouldUpdateCategoryInPlace() {
        Category saved = save(category(null, "Antes", "antes", null, true));

        saved.setName("Depois");
        save(saved);

        assertThat(inTx(() -> adapter.findById(saved.getId()))).isPresent()
                .get().extracting(Category::getName).isEqualTo("Depois");
    }

    @Test
    void shouldDeleteLeafAndReflectInTree() {
        Category electronics = save(category(null, "Eletronicos", "eletronicos", null, true));
        Category phones = save(category(null, "Celulares", "celulares", electronics, true));

        inTx(() -> {
            adapter.delete(phones.getId());
            return null;
        });

        assertThat(inTx(() -> adapter.findAll()))
                .singleElement()
                .satisfies(root -> assertThat(root.getChildren()).isEmpty());
    }

    private Category save(Category category) {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        Category saved = adapter.save(category);
        tx.commit();
        em.clear();
        return saved;
    }

    private Category category(Long id, String name, String slug, Category parent, boolean active) {
        return new Category(id, name, new Slug(slug), parent, 0, active);
    }
}
