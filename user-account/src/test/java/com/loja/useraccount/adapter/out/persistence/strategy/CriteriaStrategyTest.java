package com.loja.useraccount.adapter.out.persistence.strategy;

import com.loja.useraccount.application.dto.UserSearchCriteria;
import com.loja.useraccount.domain.model.UserStatus;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

class CriteriaStrategyTest {

    @Mock
    private TypedQuery<?> query;

    private final EmailCriteriaStrategy emailStrategy = new EmailCriteriaStrategy();
    private final StatusCriteriaStrategy statusStrategy = new StatusCriteriaStrategy();
    private AutoCloseable mockCloseable;

    @BeforeEach
    void setUp() {
        mockCloseable = MockitoAnnotations.openMocks(this);
    }

    @Test
    void emailStrategyShouldSupportNonNullNonBlankEmail() {
        assertThat(emailStrategy.supports(new UserSearchCriteria("test@x.com", null, null))).isTrue();
        assertThat(emailStrategy.supports(new UserSearchCriteria(null, null, null))).isFalse();
        assertThat(emailStrategy.supports(new UserSearchCriteria("", null, null))).isFalse();
        assertThat(emailStrategy.supports(new UserSearchCriteria("  ", null, null))).isFalse();
    }

    @Test
    void emailStrategyShouldReturnCorrectFragment() {
        assertThat(emailStrategy.conditionFragment()).isEqualTo("u.email LIKE :email");
    }

    @Test
    void emailStrategyShouldApplyParameter() {
        emailStrategy.applyParameter(query, new UserSearchCriteria("john", null, null));
        verify(query).setParameter("email", "%john%");
    }

    @Test
    void statusStrategyShouldSupportNonNullStatus() {
        assertThat(statusStrategy.supports(new UserSearchCriteria(null, UserStatus.ACTIVE, null))).isTrue();
        assertThat(statusStrategy.supports(new UserSearchCriteria(null, null, null))).isFalse();
    }

    @Test
    void statusStrategyShouldReturnCorrectFragment() {
        assertThat(statusStrategy.conditionFragment()).isEqualTo("u.status = :status");
    }

    @Test
    void statusStrategyShouldApplyParameter() {
        statusStrategy.applyParameter(query, new UserSearchCriteria(null, UserStatus.LOCKED, null));
        verify(query).setParameter(eq("status"), eq(UserStatus.LOCKED));
    }

    @Test
    void strategiesShouldSupportCombinedCriteria() {
        var both = new UserSearchCriteria("test@x.com", UserStatus.ACTIVE, null);
        assertThat(emailStrategy.supports(both)).isTrue();
        assertThat(statusStrategy.supports(both)).isTrue();
    }

    @Test
    void strategiesShouldSupportOnlyEmail() {
        var onlyEmail = new UserSearchCriteria("test@x.com", null, null);
        assertThat(emailStrategy.supports(onlyEmail)).isTrue();
        assertThat(statusStrategy.supports(onlyEmail)).isFalse();
    }

    @Test
    void strategiesShouldSupportOnlyStatus() {
        var onlyStatus = new UserSearchCriteria(null, UserStatus.INACTIVE, null);
        assertThat(emailStrategy.supports(onlyStatus)).isFalse();
        assertThat(statusStrategy.supports(onlyStatus)).isTrue();
    }

    @Test
    void strategiesShouldHandleEmptyCriteria() {
        var empty = new UserSearchCriteria(null, null, null);
        assertThat(emailStrategy.supports(empty)).isFalse();
        assertThat(statusStrategy.supports(empty)).isFalse();
    }
}
