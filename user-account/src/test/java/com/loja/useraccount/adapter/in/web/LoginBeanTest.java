package com.loja.useraccount.adapter.in.web;

import com.loja.useraccount.adapter.auth.LoginAuthenticationMechanism;
import com.loja.useraccount.domain.model.Email;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.model.UserPassword;
import com.loja.useraccount.domain.model.UserProfile;
import com.loja.useraccount.domain.port.in.FindUserUseCase;
import com.loja.useraccount.domain.port.in.LoginUseCase;
import com.loja.useraccount.domain.port.in.LogoutUseCase;
import com.loja.useraccount.domain.port.out.PasswordHasherPort;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the login/logout flow. The central assertion in this class is
 * {@link #login_validCredentials_authenticatesExactlyOnceThenEstablishesSession()}: it
 * fails if {@code LoginBean} ever goes back to calling both {@code LoginUseCase.login(...)}
 * and {@code HttpServletRequest.login(...)} for the same attempt (the bug this test guards
 * against — two Argon2id verifications and two persists per successful login).
 */
class LoginBeanTest {

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "password1234";

    private final LoginUseCase loginUseCase = mock(LoginUseCase.class);
    private final LogoutUseCase logoutUseCase = mock(LogoutUseCase.class);
    private final FindUserUseCase findUserUseCase = mock(FindUserUseCase.class);

    private final FacesContext facesContext = mock(FacesContext.class);
    private final ExternalContext externalContext = mock(ExternalContext.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    private MockedStatic<FacesContext> facesContextStatic;
    private LoginBean bean;

    @BeforeEach
    void setUp() {
        facesContextStatic = mockStatic(FacesContext.class);
        facesContextStatic.when(FacesContext::getCurrentInstance).thenReturn(facesContext);
        when(facesContext.getExternalContext()).thenReturn(externalContext);
        when(externalContext.getRequest()).thenReturn(request);

        bean = new LoginBean(loginUseCase, logoutUseCase, findUserUseCase);
        bean.setEmail(EMAIL);
        bean.setPassword(PASSWORD);
    }

    @AfterEach
    void tearDown() {
        facesContextStatic.close();
    }

    @Test
    void login_validCredentials_authenticatesExactlyOnceThenEstablishesSession() throws ServletException {
        when(findUserUseCase.findByEmail(EMAIL)).thenReturn(Optional.of(activeUser()));

        String outcome = bean.login();

        assertThat(outcome).isEqualTo("/product-catalog/catalog.xhtml?faces-redirect=true");

        // The container path ran exactly once...
        verify(request).setAttribute(LoginAuthenticationMechanism.USERNAME_ATTRIBUTE, EMAIL);
        verify(request).setAttribute(LoginAuthenticationMechanism.PASSWORD_ATTRIBUTE, PASSWORD);
        verify(request, times(1)).login(EMAIL, PASSWORD);
        verify(request).changeSessionId();

        // ...session was established WITHOUT re-checking the password...
        verify(loginUseCase, times(1)).establishSession(EMAIL);

        // ...and the heavy, independently-hashing LoginUseCase.login(...) was never called.
        // This is the assertion that fails if the old double-verification path comes back.
        verify(loginUseCase, never()).login(anyString(), anyString());

        verify(facesContext, never()).addMessage(any(), any(FacesMessage.class));
    }

    @Test
    void login_unknownEmail_showsGenericMessageAndNeverCallsFindResultDirectly() throws ServletException {
        when(findUserUseCase.findByEmail(EMAIL)).thenReturn(Optional.empty());
        doThrow(new ServletException("invalid")).when(request).login(EMAIL, PASSWORD);

        String outcome = bean.login();

        assertThat(outcome).isNull();
        // Falls through to the container attempt exactly like a known user with a wrong
        // password would -- this bean never confirms whether the email is registered.
        verify(request, times(1)).login(EMAIL, PASSWORD);
        verify(facesContext).addMessage(isNull(),
                argThat((FacesMessage m) -> "Invalid email or password".equals(m.getDetail())));
        verify(loginUseCase, never()).establishSession(anyString());
    }

    @Test
    void login_wrongPassword_containerRejectsAndSessionIsNeverEstablished() throws ServletException {
        when(findUserUseCase.findByEmail(EMAIL)).thenReturn(Optional.of(activeUser()));
        doThrow(new ServletException("invalid")).when(request).login(EMAIL, PASSWORD);

        String outcome = bean.login();

        assertThat(outcome).isNull();
        verify(request, times(1)).login(EMAIL, PASSWORD);
        verify(facesContext).addMessage(isNull(),
                argThat((FacesMessage m) -> "Invalid email or password".equals(m.getDetail())));
        verify(loginUseCase, never()).establishSession(anyString());
        verify(loginUseCase, never()).login(anyString(), anyString());
    }

    @Test
    void login_lockedAccount_shortCircuitsBeforeTouchingTheContainer() throws ServletException {
        User locked = activeUser();
        // Five failed attempts flip the domain status to LOCKED (see UserTest for the
        // domain-level assertion); here we only need canLogin() == false.
        for (int i = 0; i < 5; i++) {
            locked.recordLoginFailure();
        }
        when(findUserUseCase.findByEmail(EMAIL)).thenReturn(Optional.of(locked));

        String outcome = bean.login();

        assertThat(outcome).isNull();
        verify(facesContext).addMessage(isNull(),
                argThat((FacesMessage m) -> "Account is locked or inactive".equals(m.getDetail())));

        // The whole point of the pre-flight check: no Argon2id verification is even
        // attempted for an account we already know cannot log in.
        verify(request, never()).login(anyString(), anyString());
        verify(loginUseCase, never()).establishSession(anyString());
    }

    @Test
    void logout_invalidatesBothTheContainerIdentityAndTheApplicationSession() throws ServletException {
        String outcome = bean.logout();

        assertThat(outcome).isEqualTo("/user-account/login.xhtml?faces-redirect=true");
        verify(request).logout();
        verify(logoutUseCase).logout(isNull());
    }

    private static User activeUser() {
        PasswordHasherPort hasher = new PasswordHasherPort() {
            @Override
            public String hash(String plainPassword) {
                return "hash:" + plainPassword;
            }

            @Override
            public boolean verify(String plainPassword, String hash) {
                return ("hash:" + plainPassword).equals(hash);
            }
        };
        return User.create(new Email(EMAIL), UserPassword.hash(PASSWORD, hasher),
                UserProfile.fromFullName("Test User"));
    }
}
