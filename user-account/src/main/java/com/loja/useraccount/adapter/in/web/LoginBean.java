package com.loja.useraccount.adapter.in.web;

import com.loja.useraccount.adapter.auth.LoginAuthenticationMechanism;
import com.loja.useraccount.domain.exception.InvalidPasswordException;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.port.in.FindUserUseCase;
import com.loja.useraccount.domain.port.in.LoginUseCase;
import com.loja.useraccount.domain.port.in.LogoutUseCase;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.SecurityContext;
import jakarta.security.enterprise.authentication.mechanism.http.AuthenticationParameters;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;

/**
 * JSF managed bean for login/logout.
 *
 * <p><b>Single credential check, by design.</b> Password verification happens exactly
 * once per attempt, inside the container: {@link SecurityContext#authenticate} re-enters
 * {@link LoginAuthenticationMechanism}, which delegates to the Jakarta Security
 * {@code IdentityStoreHandler} (backed by {@code UserIdentityStore} →
 * {@link com.loja.useraccount.domain.port.in.ValidateCredentialsUseCase}). That single
 * call already verifies the Argon2id hash, records the failed/successful attempt, and
 * enforces the 5-strikes lockout — see {@code User.authenticate}.
 *
 * <p>Note: {@link HttpServletRequest#login(String, String)} must <b>not</b> be used here.
 * Open Liberty forbids it while a JASPI {@code HttpAuthenticationMechanism} is active
 * ("The login method may not be invoked while JASPI authentication is active"). The
 * supported programmatic-login entry point is {@code SecurityContext.authenticate(...)},
 * which invokes the registered mechanism with {@code isAuthenticationRequest() == true}.
 *
 * <p>This bean therefore <b>must not</b> call {@link LoginUseCase#login(String, String)}
 * before or after {@code SecurityContext.authenticate(...)}: that method runs the exact
 * same domain check independently (its own Argon2id comparison + its own save), which
 * used to run back-to-back with the container's check on every successful login — twice
 * the hashing cost (Argon2id is deliberately expensive) and two separate persists for one
 * login. {@link LoginUseCase#login(String, String)} remains the right entry point for
 * non-container callers (a future REST API, a CLI, tests) — the web layer just isn't
 * one of them once real Jakarta Security RBAC is in place.
 *
 * <p>To still show a precise "account is locked" message (instead of a generic
 * "invalid credentials" for every failure) without paying for a second password
 * verification, a cheap read-only check ({@link FindUserUseCase#findByEmail(String)},
 * no hashing involved) runs <i>before</i> attempting the container login. Unknown
 * emails fall through to the container path and surface the same generic message as a
 * wrong password — this bean never reveals whether an email is registered.
 *
 * <p>On success, {@link LoginUseCase#establishSession(String)} is called — not
 * {@code login(...)} — to open the application-level session ({@code SessionPort},
 * consumed by {@code UserBean.getCurrentUser()} to render profile data) and publish
 * {@code UserLoggedInEvent}, <i>without</i> re-checking the password. Its Javadoc says
 * exactly this: "Used after the container has validated the caller via the security
 * identity store."
 */
@Named
@RequestScoped
public class LoginBean {

    @Inject
    private LoginUseCase loginUseCase;

    @Inject
    private LogoutUseCase logoutUseCase;

    @Inject
    private FindUserUseCase findUserUseCase;

    @Inject
    private SecurityContext securityContext;

    @NotBlank(message = "E-mail is required")
    @Email(message = "Invalid e-mail format")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    /** Required no-arg constructor for CDI proxying (implicit before, explicit now that a second constructor exists). */
    public LoginBean() {
    }

    /** Test-only constructor: lets unit tests inject mocked use cases without a CDI container. */
    LoginBean(LoginUseCase loginUseCase, LogoutUseCase logoutUseCase, FindUserUseCase findUserUseCase,
              SecurityContext securityContext) {
        this.loginUseCase = loginUseCase;
        this.logoutUseCase = logoutUseCase;
        this.findUserUseCase = findUserUseCase;
        this.securityContext = securityContext;
    }

    public String login() {
        // Cheap pre-flight check: a repository read, zero password hashing. Only lets us
        // show a specific "locked/inactive" message; an unknown email intentionally falls
        // through to the generic failure below (never confirm whether an email exists).
        Optional<User> existing = findUserUseCase.findByEmail(email);
        if (existing.isPresent() && !existing.get().canLogin()) {
            addError("Login failed", "Account is locked or inactive");
            return null;
        }

        HttpServletRequest request = currentRequest();
        request.setAttribute(LoginAuthenticationMechanism.USERNAME_ATTRIBUTE, email);
        request.setAttribute(LoginAuthenticationMechanism.PASSWORD_ATTRIBUTE, password);
        // The ONE password verification for this attempt: SecurityContext.authenticate
        // re-enters LoginAuthenticationMechanism -> IdentityStoreHandler -> UserIdentityStore ->
        // ValidateCredentialsUseCase, which hashes, records the attempt, and saves.
        AuthenticationStatus status = securityContext.authenticate(request, currentResponse(),
                AuthenticationParameters.withParams()
                        .credential(new UsernamePasswordCredential(email, password)));
        if (status != AuthenticationStatus.SUCCESS) {
            addError("Login failed", "Invalid email or password");
            return null;
        }
        request.changeSessionId();

        try {
            // No password check here — the container already established the caller.
            // Only opens the app-level session and publishes the login event.
            loginUseCase.establishSession(email);
        } catch (InvalidPasswordException e) {
            // Practically unreachable (the container just validated this exact email),
            // kept as a defensive guard instead of letting a raw exception surface.
            addError("Login failed", "Unable to establish your session. Please try again.");
            return null;
        }

        return "/product-catalog/catalog.xhtml?faces-redirect=true";
    }

    public String logout() {
        HttpServletRequest request = currentRequest();
        try {
            request.logout();
        } catch (ServletException e) {
            // session invalidation below still clears the local state
        }
        logoutUseCase.logout(null);
        return "/user-account/login.xhtml?faces-redirect=true";
    }

    private static HttpServletRequest currentRequest() {
        return (HttpServletRequest) FacesContext.getCurrentInstance()
                .getExternalContext().getRequest();
    }

    private static HttpServletResponse currentResponse() {
        return (HttpServletResponse) FacesContext.getCurrentInstance()
                .getExternalContext().getResponse();
    }

    private static void addError(String summary, String detail) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, summary, detail));
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
