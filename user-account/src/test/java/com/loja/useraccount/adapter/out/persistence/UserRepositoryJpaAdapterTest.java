package com.loja.useraccount.adapter.out.persistence;

import com.loja.useraccount.application.dto.PageResult;
import com.loja.useraccount.application.dto.UserSearchCriteria;
import com.loja.useraccount.domain.model.Email;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.model.UserPassword;
import com.loja.useraccount.domain.model.UserProfile;
import com.loja.useraccount.domain.model.UserStatus;
import com.loja.useraccount.domain.port.out.PasswordHasherPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserRepositoryJpaAdapterTest {

    private static final PasswordHasherPort TEST_HASHER = new PasswordHasherPort() {
        @Override public String hash(String plainPassword) { return "argon2:" + plainPassword; }
        @Override public boolean verify(String plainPassword, String hash) {
            return ("argon2:" + plainPassword).equals(hash);
        }
    };

    @Mock private EntityManager em;
    @Mock private TypedQuery<UserJpaEntity> query;
    @Mock private TypedQuery<Long> countQuery;
    @Captor private ArgumentCaptor<UserJpaEntity> entityCaptor;

    private UserRepositoryAdapter adapter;
    private AutoCloseable mockCloseable;

    @BeforeEach
    void setUp() {
        mockCloseable = MockitoAnnotations.openMocks(this);
        adapter = new UserRepositoryAdapter();
        adapter.em = em;
    }

    @Test
    void shouldPersistUserOnSave() {
        User user = User.create(
                new Email("save@example.com"),
                UserPassword.hash("Password1", TEST_HASHER),
                UserProfile.fromFullName("Save Test")
        );

        User result = adapter.save(user);

        verify(em).merge(entityCaptor.capture());
        UserJpaEntity captured = entityCaptor.getValue();
        assertThat(captured.toDomain().getEmail().getValue()).isEqualTo("save@example.com");
        assertThat(result).isSameAs(user);
    }

    @Test
    void shouldFindByIdWhenExists() {
        UserJpaEntity entity = UserJpaEntity.fromDomain(User.create(
                new Email("find-id@example.com"),
                UserPassword.hash("Password1", TEST_HASHER),
                UserProfile.fromFullName("Find By ID")
        ));
        when(em.find(UserJpaEntity.class, "user-abc")).thenReturn(entity);

        Optional<User> found = adapter.findById("user-abc");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(entity.getId());
        assertThat(found.get().getEmail().getValue()).isEqualTo("find-id@example.com");
    }

    @Test
    void shouldReturnEmptyWhenFindByIdNotFound() {
        when(em.find(UserJpaEntity.class, "nonexistent")).thenReturn(null);

        Optional<User> found = adapter.findById("nonexistent");

        assertThat(found).isEmpty();
    }

    @Test
    void shouldFindByEmailWhenExists() {
        UserJpaEntity entity = UserJpaEntity.fromDomain(User.create(
                new Email("find-email@example.com"),
                UserPassword.hash("Password1", TEST_HASHER),
                UserProfile.fromFullName("Find By Email")
        ));
        when(em.createQuery(anyString(), eq(UserJpaEntity.class))).thenReturn(query);
        when(query.setParameter("email", "find-email@example.com")).thenReturn(query);
        when(query.getResultStream()).thenReturn(Stream.of(entity));

        Optional<User> found = adapter.findByEmail("find-email@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail().getValue()).isEqualTo("find-email@example.com");
    }

    @Test
    void shouldReturnEmptyWhenFindByEmailNotFound() {
        when(em.createQuery(anyString(), eq(UserJpaEntity.class))).thenReturn(query);
        when(query.setParameter("email", "missing@example.com")).thenReturn(query);
        when(query.getResultStream()).thenReturn(Stream.empty());

        Optional<User> found = adapter.findByEmail("missing@example.com");

        assertThat(found).isEmpty();
    }

    @Test
    void shouldRemoveUserOnDelete() {
        UserJpaEntity entity = UserJpaEntity.fromDomain(User.create(
                new Email("delete@example.com"),
                UserPassword.hash("Password1", TEST_HASHER),
                UserProfile.fromFullName("Delete Test")
        ));
        when(em.find(UserJpaEntity.class, "user-to-delete")).thenReturn(entity);

        adapter.delete("user-to-delete");

        verify(em).remove(entity);
    }

    @Test
    void shouldDoNothingWhenDeleteNonexistent() {
        when(em.find(UserJpaEntity.class, "already-gone")).thenReturn(null);

        adapter.delete("already-gone");

        verify(em).find(UserJpaEntity.class, "already-gone");
    }

    @Test
    void shouldFindAllWithNoCriteria() {
        UserSearchCriteria criteria = new UserSearchCriteria(null, null, null);
        UserJpaEntity entity = UserJpaEntity.fromDomain(User.create(
                new Email("page@example.com"),
                UserPassword.hash("Password1", TEST_HASHER),
                UserProfile.fromFullName("Page Test")
        ));

        when(em.createQuery("SELECT u FROM UserJpaEntity u ORDER BY u.email", UserJpaEntity.class)).thenReturn(query);
        when(em.createQuery("SELECT COUNT(u) FROM UserJpaEntity u", Long.class)).thenReturn(countQuery);
        when(query.setFirstResult(0)).thenReturn(query);
        when(query.setMaxResults(10)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(entity));
        when(countQuery.getSingleResult()).thenReturn(1L);

        PageResult<User> result = adapter.findAll(0, 10, criteria);

        assertThat(result.items()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.page()).isZero();
        assertThat(result.pageSize()).isEqualTo(10);
    }

    @Test
    void shouldFindAllWithEmailCriteria() {
        UserSearchCriteria criteria = new UserSearchCriteria("test@", null, null);
        UserJpaEntity entity = UserJpaEntity.fromDomain(User.create(
                new Email("test@example.com"),
                UserPassword.hash("Password1", TEST_HASHER),
                UserProfile.fromFullName("Criteria Test")
        ));

        when(em.createQuery("SELECT u FROM UserJpaEntity u WHERE u.email LIKE :email ORDER BY u.email", UserJpaEntity.class))
                .thenReturn(query);
        when(em.createQuery("SELECT COUNT(u) FROM UserJpaEntity u WHERE u.email LIKE :email", Long.class))
                .thenReturn(countQuery);
        when(query.setParameter("email", "%test@%")).thenReturn(query);
        when(countQuery.setParameter("email", "%test@%")).thenReturn(countQuery);
        when(query.setFirstResult(0)).thenReturn(query);
        when(query.setMaxResults(20)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(entity));
        when(countQuery.getSingleResult()).thenReturn(1L);

        PageResult<User> result = adapter.findAll(0, 20, criteria);

        assertThat(result.items()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void shouldFindAllWithStatusCriteria() {
        UserSearchCriteria criteria = new UserSearchCriteria(null, UserStatus.ACTIVE, null);
        UserJpaEntity entity = UserJpaEntity.fromDomain(User.create(
                new Email("active@example.com"),
                UserPassword.hash("Password1", TEST_HASHER),
                UserProfile.fromFullName("Active User")
        ));

        when(em.createQuery("SELECT u FROM UserJpaEntity u WHERE u.status = :status ORDER BY u.email", UserJpaEntity.class))
                .thenReturn(query);
        when(em.createQuery("SELECT COUNT(u) FROM UserJpaEntity u WHERE u.status = :status", Long.class))
                .thenReturn(countQuery);
        when(query.setParameter("status", UserStatus.ACTIVE)).thenReturn(query);
        when(countQuery.setParameter("status", UserStatus.ACTIVE)).thenReturn(countQuery);
        when(query.setFirstResult(10)).thenReturn(query);
        when(query.setMaxResults(10)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(entity));
        when(countQuery.getSingleResult()).thenReturn(1L);

        PageResult<User> result = adapter.findAll(1, 10, criteria);

        assertThat(result.items()).hasSize(1);
        assertThat(result.page()).isEqualTo(1);
    }
}
