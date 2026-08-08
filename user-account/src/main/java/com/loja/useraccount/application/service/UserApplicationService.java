package com.loja.useraccount.application.service;

import com.loja.shared.domain.Result;
import com.loja.shared.event.DomainEventPublisherPort;
import com.loja.useraccount.application.dto.AuditLogSearchCriteria;
import com.loja.useraccount.application.dto.PageResult;
import com.loja.useraccount.application.dto.UserSearchCriteria;
import com.loja.useraccount.domain.event.AddressAddedEvent;
import com.loja.useraccount.domain.event.AddressRemovedEvent;
import com.loja.useraccount.domain.event.PasswordChangedEvent;
import com.loja.useraccount.domain.event.PasswordResetRequestedEvent;
import com.loja.useraccount.domain.event.RoleAssignedEvent;
import com.loja.useraccount.domain.event.UserBlockedEvent;
import com.loja.useraccount.domain.event.UserLoggedInEvent;
import com.loja.useraccount.domain.event.UserRegisteredEvent;
import com.loja.useraccount.domain.event.UserUnblockedEvent;
import com.loja.useraccount.domain.exception.EmailAlreadyRegisteredException;
import com.loja.useraccount.domain.exception.InsufficientPermissionException;
import com.loja.useraccount.domain.exception.InvalidPasswordException;
import com.loja.useraccount.domain.exception.UserNotFoundException;
import com.loja.useraccount.domain.model.Address;
import com.loja.useraccount.domain.model.Email;
import com.loja.useraccount.domain.model.Role;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.model.UserPassword;
import com.loja.useraccount.domain.model.UserProfile;
import com.loja.useraccount.domain.port.in.AddAddressUseCase;
import com.loja.useraccount.domain.port.in.AssignRoleUseCase;
import com.loja.useraccount.domain.port.in.ChangePasswordUseCase;
import com.loja.useraccount.domain.port.in.CheckUserRoleUseCase;
import com.loja.useraccount.domain.port.in.DeleteAddressUseCase;
import com.loja.useraccount.domain.port.in.FindUserUseCase;
import com.loja.useraccount.domain.port.in.GetCurrentUserUseCase;
import com.loja.useraccount.domain.port.in.GetUserProfileUseCase;
import com.loja.useraccount.domain.port.in.ListAddressesUseCase;
import com.loja.useraccount.domain.port.in.ListUsersUseCase;
import com.loja.useraccount.domain.port.in.LoginUseCase;
import com.loja.useraccount.domain.port.in.LogoutUseCase;
import com.loja.useraccount.domain.port.in.RegisterUserUseCase;
import com.loja.useraccount.domain.port.in.RequestPasswordResetUseCase;
import com.loja.useraccount.domain.port.in.ResetPasswordUseCase;
import com.loja.useraccount.domain.port.in.SetDefaultAddressUseCase;
import com.loja.useraccount.domain.port.in.UpdateAddressUseCase;
import com.loja.useraccount.domain.port.in.UpdateProfileUseCase;
import com.loja.useraccount.domain.port.in.ValidateCredentialsUseCase;
import com.loja.useraccount.domain.port.out.PasswordHasherPort;
import com.loja.useraccount.domain.port.out.SessionPort;
import com.loja.useraccount.domain.port.out.UserRepositoryPort;
import com.loja.useraccount.domain.validation.DomainError;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
@Transactional
public class UserApplicationService
        implements RegisterUserUseCase, LoginUseCase, LogoutUseCase,
                   FindUserUseCase, GetCurrentUserUseCase, GetUserProfileUseCase,
                   UpdateProfileUseCase, ChangePasswordUseCase,
                   AddAddressUseCase, UpdateAddressUseCase, DeleteAddressUseCase,
                   SetDefaultAddressUseCase, ListAddressesUseCase,
                   AssignRoleUseCase, ListUsersUseCase, CheckUserRoleUseCase,
                   ValidateCredentialsUseCase,
                   RequestPasswordResetUseCase, ResetPasswordUseCase,
                   com.loja.useraccount.domain.port.in.ChangeUserStatusUseCase,
                   com.loja.useraccount.domain.port.in.ListAuditLogsUseCase {

    private final UserRepositoryPort userRepository;
    private final PasswordHasherPort passwordHasher;
    private final SessionPort session;
    private final DomainEventPublisherPort eventPublisher;
    private final com.loja.useraccount.domain.port.out.AuditLogQueryPort auditLogQueryPort;

    @Inject
    public UserApplicationService(UserRepositoryPort userRepository,
                                   PasswordHasherPort passwordHasher,
                                   SessionPort session,
                                   DomainEventPublisherPort eventPublisher,
                                   com.loja.useraccount.domain.port.out.AuditLogQueryPort auditLogQueryPort) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.session = session;
        this.eventPublisher = eventPublisher;
        this.auditLogQueryPort = auditLogQueryPort;
    }

    @Override
    public User register(String email, String password, String fullName) {
        var validationResult = validateRegistration(email, password, fullName);
        if (validationResult.isFailure()) {
            DomainError err = validationResult.getError().get();
            if (err instanceof DomainError.EmailAlreadyTaken) {
                throw new EmailAlreadyRegisteredException(err.message());
            }
            throw new IllegalArgumentException(err.message());
        }

        User user = validationResult.getValue().get();
        user = userRepository.save(user);

        eventPublisher.publish(new UserRegisteredEvent(user.getId(),
                user.getEmail().getValue(), user.getFullName()));
        return user;
    }

    @Override
    public User login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidPasswordException("Invalid email or password"));

        if (!user.canLogin()) {
            throw new InvalidPasswordException("Account is locked or inactive");
        }

        if (!user.authenticate(password, passwordHasher)) {
            userRepository.save(user);
            throw new InvalidPasswordException("Invalid email or password");
        }

        return completeLogin(userRepository.save(user));
    }

    @Override
    public Optional<User> validateCredentials(String email, String plainPassword) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !user.canLogin()) {
            return Optional.empty();
        }
        if (!user.authenticate(plainPassword, passwordHasher)) {
            userRepository.save(user);
            return Optional.empty();
        }
        return Optional.of(userRepository.save(user));
    }

    @Override
    public void establishSession(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidPasswordException("Invalid email or password"));
        completeLogin(user);
    }

    private User completeLogin(User user) {
        session.createSession(user);
        eventPublisher.publish(new UserLoggedInEvent(user.getId(), user.getEmail().getValue()));
        return user;
    }

    @Override
    public void logout(String userId) {
        session.invalidateSession();
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public Optional<User> findById(String userId) {
        return userRepository.findById(userId);
    }

    @Override
    public Optional<User> getCurrentUser() {
        return session.getCurrentUser();
    }

    @Override
    public User updateProfile(String userId, String fullName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        user.updateProfile(UserProfile.fromFullName(fullName));
        return userRepository.save(user);
    }

    @Override
    public void changePassword(String userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        user.changePassword(currentPassword, newPassword, passwordHasher);
        userRepository.save(user);
        eventPublisher.publish(new PasswordChangedEvent(userId));
    }

    @Override
    public Address addAddress(String userId, String street, String number, String complement,
                              String neighborhood, String city, String state,
                              String postalCode, String label, boolean setAsDefault) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        Address address = new Address(null, street, number, complement, neighborhood,
                city, state, postalCode, label, setAsDefault);
        user.addAddress(address, setAsDefault);
        userRepository.save(user);
        eventPublisher.publish(new AddressAddedEvent(userId, address));
        return address;
    }

    @Override
    public void deleteAddress(String userId, Long addressId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        user.removeAddress(addressId);
        userRepository.save(user);
        eventPublisher.publish(new AddressRemovedEvent(userId, addressId));
    }

    @Override
    public Set<Address> listAddresses(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        return user.getAddresses();
    }

    @Override
    public void assignRole(String userId, Role role) {
        String actorId = session.getCurrentUser()
                .map(User::getId)
                .orElseThrow(() -> new InsufficientPermissionException(
                        "Role assignment requires an authenticated user"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        user.addRole(role);
        userRepository.save(user);
        eventPublisher.publish(new RoleAssignedEvent(user.getId(), role, actorId));
    }

    @Override
    public PageResult<User> listUsers(int page, int pageSize, UserSearchCriteria criteria) {
        return userRepository.findAll(page, pageSize, criteria);
    }

    @Override
    public boolean currentUserHasRole(Role role) {
        return session.getCurrentUser()
                .map(user -> user.hasRole(role))
                .orElse(false);
    }

    @Override
    public Optional<User> getUserProfile(String userId) {
        return userRepository.findById(userId);
    }

    @Override
    public Address updateAddress(String userId, Long addressId, String street, String number,
                                 String complement, String neighborhood, String city,
                                 String state, String postalCode, String label, boolean setAsDefault) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        Address newAddress = new Address(addressId, street, number, complement, neighborhood,
                city, state, postalCode, label, setAsDefault);
        user.removeAddress(addressId);
        user.addAddress(newAddress, setAsDefault);
        userRepository.save(user);
        return newAddress;
    }

    @Override
    public void setDefaultAddress(String userId, Long addressId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        user.setDefaultAddress(addressId);
        userRepository.save(user);
    }

    @Override
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            String token = java.util.UUID.randomUUID().toString();
            user.requestPasswordReset(token);
            userRepository.save(user);
            eventPublisher.publish(new PasswordResetRequestedEvent(
                    user.getId(), user.getEmail().getValue(), token));
        });
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetToken(token)
                .orElseThrow(() -> new InvalidPasswordException("Invalid or expired password reset token"));
        user.resetPassword(token, newPassword, passwordHasher);
        userRepository.save(user);
        eventPublisher.publish(new PasswordChangedEvent(user.getId()));
    }

    private Result<User, DomainError> validateRegistration(String email, String password, String fullName) {
        var registered = userRepository.findByEmail(email);
        if (registered.isPresent()) {
            return Result.failure(new DomainError.EmailAlreadyTaken("Email already registered: " + email));
        }
        return User.tryRegister(email, password, fullName, passwordHasher);
    }

    @Override
    public void blockUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        user.deactivate();
        userRepository.save(user);
        eventPublisher.publish(new UserBlockedEvent(user.getId()));
    }

    @Override
    public void unblockUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        user.activate();
        userRepository.save(user);
        eventPublisher.publish(new UserUnblockedEvent(user.getId()));
    }

    @Override
    public PageResult<com.loja.useraccount.domain.model.AuditLogEvent> listAuditLogs(AuditLogSearchCriteria criteria, int page, int pageSize) {
        if (!currentUserHasRole(Role.ADMIN)) {
            throw new InsufficientPermissionException("Only admins can view audit logs");
        }
        return auditLogQueryPort.findAuditLogs(criteria, page, pageSize);
    }

    @Override
    public java.util.List<String> distinctEventTypes() {
        if (!currentUserHasRole(Role.ADMIN)) {
            throw new InsufficientPermissionException("Only admins can view audit logs");
        }
        return auditLogQueryPort.distinctEventTypes();
    }
}
