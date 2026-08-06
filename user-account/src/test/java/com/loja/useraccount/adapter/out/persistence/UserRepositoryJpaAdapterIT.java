package com.loja.useraccount.adapter.out.persistence;

import com.loja.useraccount.domain.model.Address;
import com.loja.useraccount.domain.model.Email;
import com.loja.useraccount.domain.model.Role;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.model.UserGrowthPoint;
import com.loja.useraccount.domain.model.UserPassword;
import com.loja.useraccount.domain.model.UserProfile;
import com.loja.useraccount.domain.model.UserStatus;
import com.loja.useraccount.domain.port.out.PasswordHasherPort;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

    @Test
    void shouldCountAllUsers() {
        long before = adapter.count();

        EntityTransaction tx = em.getTransaction();
        tx.begin();
        adapter.save(createUser("count-1@example.com", "Count One"));
        adapter.save(createUser("count-2@example.com", "Count Two"));
        tx.commit();
        em.clear();

        long total = adapter.count();

        assertThat(total).isEqualTo(before + 2);
    }

    @Test
    void shouldCountUsersCreatedSince() {
        Instant start = Instant.now().minusSeconds(1);

        EntityTransaction tx = em.getTransaction();
        tx.begin();
        adapter.save(createUser("since-1@example.com", "Since One"));
        adapter.save(createUser("since-2@example.com", "Since Two"));
        tx.commit();
        em.clear();

        long count = adapter.countCreatedSince(start);

        assertThat(count).isGreaterThanOrEqualTo(2);
    }

    @Test
    void shouldBuildUserGrowthSeriesBucketedByDay() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now();
        Instant from = today.atStartOfDay(zone).toInstant();
        Instant to = from.plus(2, ChronoUnit.DAYS);

        long before = adapter.userGrowthSeries(from, to).stream()
                .filter(point -> point.date().equals(today))
                .mapToLong(UserGrowthPoint::count)
                .sum();

        EntityTransaction tx = em.getTransaction();
        tx.begin();
        adapter.save(createUser("growth-1@example.com", "Growth One"));
        adapter.save(createUser("growth-2@example.com", "Growth Two"));
        tx.commit();
        em.clear();

        long after = adapter.userGrowthSeries(from, to).stream()
                .filter(point -> point.date().equals(today))
                .mapToLong(UserGrowthPoint::count)
                .sum();

        assertThat(after).isEqualTo(before + 2);
    }

    @Test
    void shouldBuildUserGrowthSeriesReturnEmptyOutsideRange() {
        ZoneId zone = ZoneId.systemDefault();
        Instant from = LocalDate.now().atStartOfDay(zone).toInstant().plus(100, ChronoUnit.DAYS);

        List<UserGrowthPoint> series = adapter.userGrowthSeries(from, from.plus(1, ChronoUnit.DAYS));

        assertThat(series).isEmpty();
    }

    @Test
    void shouldCountInactiveUsersWithOldLastLogin() {
        Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);
        long before = adapter.countInactiveSince(cutoff);

        EntityTransaction tx = em.getTransaction();
        tx.begin();
        User oldLogin = createUser("churn-old@example.com", "Old Login");
        oldLogin.recordSuccessfulLogin(Instant.now().minus(120, ChronoUnit.DAYS));
        adapter.save(oldLogin);
        tx.commit();
        em.clear();

        long after = adapter.countInactiveSince(cutoff);

        assertThat(after).isEqualTo(before + 1);
    }

    @Test
    void shouldCountInactiveUsersExcludingRecentLoginsAndFreshAccounts() {
        Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);
        long before = adapter.countInactiveSince(cutoff);

        EntityTransaction tx = em.getTransaction();
        tx.begin();
        User recentLogin = createUser("churn-recent@example.com", "Recent Login");
        recentLogin.recordSuccessfulLogin(Instant.now().minus(1, ChronoUnit.DAYS));
        adapter.save(recentLogin);
        adapter.save(createUser("churn-fresh@example.com", "Fresh Account"));
        tx.commit();
        em.clear();

        long after = adapter.countInactiveSince(cutoff);

        assertThat(after).isEqualTo(before);
    }

    @Test
    void shouldCountNeverLoggedInAccountsAsInactiveAfterCutoff() {
        Instant cutoff = Instant.now().plus(90, ChronoUnit.DAYS);
        long before = adapter.countInactiveSince(cutoff);

        EntityTransaction tx = em.getTransaction();
        tx.begin();
        adapter.save(createUser("churn-never@example.com", "Never Logged In"));
        tx.commit();
        em.clear();

        long after = adapter.countInactiveSince(cutoff);

        assertThat(after).isEqualTo(before + 1);
    }

    private User createUser(String email, String fullName) {
        return User.create(
                new Email(email),
                UserPassword.hash("Password1", TEST_HASHER),
                UserProfile.fromFullName(fullName));
    }
}
