package com.loja.useraccount.application.service;

import com.loja.useraccount.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserMetricsServiceTest {

    private final UserRepositoryPort userRepository = mock(UserRepositoryPort.class);

    @Test
    void countAll_delegatesToRepository() {
        when(userRepository.count()).thenReturn(42L);
        UserMetricsService service = new UserMetricsService(userRepository);

        long count = service.countAll();

        assertThat(count).isEqualTo(42L);
        verify(userRepository).count();
    }

    @Test
    void countAll_withEmptyStore_returnsZero() {
        when(userRepository.count()).thenReturn(0L);
        UserMetricsService service = new UserMetricsService(userRepository);

        assertThat(service.countAll()).isZero();
    }

    @Test
    void countRegisteredToday_delegatesToRepositoryWithInstantBoundary() {
        when(userRepository.countCreatedSince(any())).thenReturn(3L);
        UserMetricsService service = new UserMetricsService(userRepository);

        long count = service.countRegisteredToday();

        assertThat(count).isEqualTo(3L);
        verify(userRepository).countCreatedSince(any(Instant.class));
    }

    @Test
    void countRegisteredThisMonth_delegatesToRepositoryWithInstantBoundary() {
        when(userRepository.countCreatedSince(any())).thenReturn(9L);
        UserMetricsService service = new UserMetricsService(userRepository);

        long count = service.countRegisteredThisMonth();

        assertThat(count).isEqualTo(9L);
        verify(userRepository).countCreatedSince(any(Instant.class));
    }
}
