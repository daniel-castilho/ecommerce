package com.loja.useraccount.application.service;

import com.loja.shared.event.DomainEventPublisherPort;
import com.loja.useraccount.domain.event.PasswordChangedEvent;
import com.loja.useraccount.domain.event.PasswordResetRequestedEvent;
import com.loja.useraccount.domain.event.RoleAssignedEvent;
import com.loja.useraccount.domain.event.UserLoggedInEvent;
import com.loja.useraccount.domain.exception.InsufficientPermissionException;
import com.loja.useraccount.domain.exception.InvalidPasswordException;
import com.loja.useraccount.domain.model.Email;
import com.loja.useraccount.domain.model.Role;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.model.UserPassword;
import com.loja.useraccount.domain.model.UserProfile;
import com.loja.useraccount.domain.port.out.PasswordHasherPort;
import com.loja.useraccount.domain.port.out.SessionPort;
import com.loja.useraccount.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserApplicationServiceTest {

    private final UserRepositoryPort userRepository = mock(UserRepositoryPort.class);
    private final PasswordHasherPort passwordHasher = mock(PasswordHasherPort.class);
    private final SessionPort session = mock(SessionPort.class);
    private final DomainEventPublisherPort eventPublisher = mock(DomainEventPublisherPort.class);
    private final com.loja.useraccount.domain.port.out.AuditLogQueryPort auditLogQueryPort = mock(com.loja.useraccount.domain.port.out.AuditLogQueryPort.class);

    private UserApplicationService service;

    @BeforeEach
    void setUp() {
        service = new UserApplicationService(userRepository, passwordHasher, session, eventPublisher, auditLogQueryPort);
        when(passwordHasher.hash(any(String.class)))
                .thenAnswer(inv -> "hash:" + inv.getArgument(0, String.class));
        when(passwordHasher.verify(any(String.class), any(String.class)))
                .thenAnswer(inv -> ("hash:" + inv.getArgument(0, String.class))
                        .equals(inv.getArgument(1, String.class)));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static User user(String email) {
        return User.create(new Email(email), UserPassword.hash("password1234", new PasswordHasherPort() {
            @Override
            public String hash(String plainPassword) {
                return "hash:" + plainPassword;
            }

            @Override
            public boolean verify(String plainPassword, String hash) {
                return ("hash:" + plainPassword).equals(hash);
            }
        }), UserProfile.fromFullName("Test User"));
    }

    @Test
    void shouldRequestPasswordResetForExistingUser() {
        User user = user("reset@example.com");
        when(userRepository.findByEmail("reset@example.com")).thenReturn(Optional.of(user));

        service.requestPasswordReset("reset@example.com");

        assertThat(user.getPasswordResetToken()).isNotBlank();
        assertThat(user.getPasswordResetTokenExpiresAt()).isNotNull();
        verify(userRepository).save(user);

        ArgumentCaptor<PasswordResetRequestedEvent> captor =
                ArgumentCaptor.forClass(PasswordResetRequestedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(user.getId());
        assertThat(captor.getValue().email()).isEqualTo("reset@example.com");
        assertThat(captor.getValue().token()).isEqualTo(user.getPasswordResetToken());
    }

    @Test
    void shouldNotRequestPasswordResetForUnknownEmail() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        service.requestPasswordReset("missing@example.com");

        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void shouldResetPasswordWithValidToken() {
        User user = user("reset2@example.com");
        user.requestPasswordReset("valid-token");
        when(userRepository.findByResetToken("valid-token")).thenReturn(Optional.of(user));

        service.resetPassword("valid-token", "newPassword123");

        assertThat(user.getPasswordHash().getHash()).isEqualTo("hash:newPassword123");
        assertThat(user.getPasswordResetToken()).isNull();
        verify(userRepository).save(user);
        verify(eventPublisher).publish(any(PasswordChangedEvent.class));
    }

    @Test
    void shouldThrowWhenResettingWithUnknownToken() {
        when(userRepository.findByResetToken("unknown-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("unknown-token", "newPassword123"))
                .isInstanceOf(InvalidPasswordException.class);

        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void shouldPublishRoleAssignedEvent() {
        User actor = user("admin@example.com");
        when(session.getCurrentUser()).thenReturn(Optional.of(actor));
        User user = user("role@example.com");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        service.assignRole(user.getId(), Role.ADMIN);

        assertThat(user.hasRole(Role.ADMIN)).isTrue();
        verify(userRepository).save(user);

        ArgumentCaptor<RoleAssignedEvent> captor =
                ArgumentCaptor.forClass(RoleAssignedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(user.getId());
        assertThat(captor.getValue().role()).isEqualTo(Role.ADMIN);
        assertThat(captor.getValue().assignedBy()).isEqualTo(actor.getId());
    }

    @Test
    void shouldRejectRoleAssignmentWithoutAuthenticatedUser() {
        when(session.getCurrentUser()).thenReturn(Optional.empty());
        User user = user("role@example.com");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.assignRole(user.getId(), Role.ADMIN))
                .isInstanceOf(InsufficientPermissionException.class);

        assertThat(user.hasRole(Role.ADMIN)).isFalse();
        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    // ------------------------------------------------------- credential validation

    @Test
    void validateCredentials_withValidCredentials_returnsUser() {
        User user = user("login@example.com");
        when(userRepository.findByEmail("login@example.com")).thenReturn(Optional.of(user));

        assertThat(service.validateCredentials("login@example.com", "password1234"))
                .containsSame(user);
        verify(userRepository).save(user);
    }

    @Test
    void validateCredentials_withWrongPassword_returnsEmpty() {
        User user = user("login@example.com");
        when(userRepository.findByEmail("login@example.com")).thenReturn(Optional.of(user));

        assertThat(service.validateCredentials("login@example.com", "wrong-password"))
                .isEmpty();
        verify(userRepository).save(user);
    }

    @Test
    void validateCredentials_withUnknownEmail_returnsEmpty() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThat(service.validateCredentials("missing@example.com", "password1234"))
                .isEmpty();
        verify(userRepository, never()).save(any());
    }

    @Test
    void validateCredentials_lockedAccount_returnsEmpty() {
        User user = user("locked@example.com");
        for (int i = 0; i < 5; i++) {
            user.recordLoginFailure();
        }
        when(userRepository.findByEmail("locked@example.com")).thenReturn(Optional.of(user));

        assertThat(service.validateCredentials("locked@example.com", "password1234"))
                .isEmpty();
        verify(userRepository, never()).save(any());
    }

    // --------------------------------------------------------------- session setup

    @Test
    void establishSession_createsSessionAndPublishesEvent() {
        User user = user("login@example.com");
        when(userRepository.findByEmail("login@example.com")).thenReturn(Optional.of(user));

        service.establishSession("login@example.com");

        verify(session).createSession(user);
        ArgumentCaptor<UserLoggedInEvent> captor = ArgumentCaptor.forClass(UserLoggedInEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(user.getId());
    }

    @Test
    void establishSession_unknownEmail_throws() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.establishSession("missing@example.com"))
                .isInstanceOf(InvalidPasswordException.class);

        verify(session, never()).createSession(any());
        verify(eventPublisher, never()).publish(any());
    }
}
