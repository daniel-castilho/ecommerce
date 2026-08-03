package com.loja.useraccount.application.service;

import com.loja.useraccount.domain.port.in.CountUsersUseCase;
import com.loja.useraccount.domain.port.out.UserRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.time.ZoneId;

/** Aggregate user counts for the admin dashboard. Depends only on the repository port (DIP). */
@ApplicationScoped
public class UserMetricsService implements CountUsersUseCase {

    private final UserRepositoryPort userRepository;

    @Inject
    public UserMetricsService(UserRepositoryPort userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public long countAll() {
        return userRepository.count();
    }

    @Override
    public long countRegisteredToday() {
        return userRepository.countCreatedSince(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    @Override
    public long countRegisteredThisMonth() {
        return userRepository.countCreatedSince(
                LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
