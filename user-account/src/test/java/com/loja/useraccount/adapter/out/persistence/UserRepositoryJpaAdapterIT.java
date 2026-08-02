package com.loja.useraccount.adapter.out.persistence;

import com.loja.useraccount.domain.model.Address;
import com.loja.useraccount.domain.model.Email;
import com.loja.useraccount.domain.model.Role;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.model.UserPassword;
import com.loja.useraccount.domain.model.UserProfile;
import com.loja.useraccount.domain.model.UserStatus;
import com.loja.useraccount.domain.port.out.PasswordHasherPort;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryJpaAdapterIT extends AbstractIntegrationTest {

    private static final PasswordHasherPort TEST_HASHER = new PasswordHasherPort() {
        @Override public String hash(String plainPassword) { return "argon2:" + plainPassword; }
        @Override public boolean verify(String plainPassword, String hash) {
            return ("argon2:" + plainPassword).equals(hash);
        }
    };

    private UserRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        setUpEntityManager();
        adapter = new UserRepositoryAdapter();
        adapter.em = em;
    }

    @Test
    void shouldPersistAndFindById() {
        User user = createUser("persist-id@example.com", "Persist ID");
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        adapter.save(user);
        tx.commit();
        em.clear();

        Optional<User> found = adapter.findById(user.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getEmail().getValue()).isEqualTo("persist-id@example.com");
        assertThat(found.get().getRoles()).contains(Role.CUSTOMER);
    }

    @Test
    void shouldFindByEmail() {
        User user = createUser("find-email@example.com", "Find Email");
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        adapter.save(user);
        tx.commit();
        em.clear();

        Optional<User> found = adapter.findByEmail("find-email@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getFullName()).isEqualTo("Find Email");
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
        Optional<User> found = adapter.findById("nonexistent-id");
        assertThat(found).isEmpty();
    }

    @Test
    void shouldUpdateUser() {
        User user = createUser("update@example.com", "Before Update");
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        adapter.save(user);
        tx.commit();
        em.clear();

        user.updateProfile(UserProfile.fromFullName("After Update"));

        tx.begin();
        adapter.save(user);
        tx.commit();
        em.clear();

        Optional<User> found = adapter.findById(user.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getFullName()).isEqualTo("After Update");
    }

    @Test
    void shouldDeleteUser() {
        User user = createUser("delete@example.com", "Delete Me");
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        adapter.save(user);
        tx.commit();
        em.clear();

        tx.begin();
        adapter.delete(user.getId());
        tx.commit();
        em.clear();

        Optional<User> found = adapter.findById(user.getId());
        assertThat(found).isEmpty();
    }

    @Test
    void shouldPersistAndRetrieveAddresses() {
        User user = createUser("address-it@example.com", "Address Test");
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        adapter.save(user);
        tx.commit();
        em.clear();

        tx.begin();
        user.addAddress(new Address(null, "Rua A", "100", null, "Centro",
                "São Paulo", "SP", "01001-000", "Home", true), true);
        adapter.save(user);
        tx.commit();
        em.clear();

        Optional<User> found = adapter.findById(user.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getAddresses()).hasSize(1);
        assertThat(found.get().getAddresses().iterator().next().getStreet()).isEqualTo("Rua A");
    }

    @Test
    void shouldPersistLockedStatus() {
        User user = createUser("locked-it@example.com", "Locked User");
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        adapter.save(user);
        tx.commit();
        em.clear();

        for (int i = 0; i < 5; i++) {
            user.recordLoginFailure();
        }

        tx.begin();
        adapter.save(user);
        tx.commit();
        em.clear();

        Optional<User> found = adapter.findById(user.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(found.get().getFailedLoginAttempts()).isEqualTo(5);
    }

    private User createUser(String email, String fullName) {
        return User.create(
                new Email(email),
                UserPassword.hash("Password1", TEST_HASHER),
                UserProfile.fromFullName(fullName));
    }
}
