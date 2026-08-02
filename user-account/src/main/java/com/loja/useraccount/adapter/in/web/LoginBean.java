package com.loja.useraccount.adapter.in.web;

import com.loja.useraccount.adapter.auth.LoginAuthenticationMechanism;
import com.loja.useraccount.domain.exception.InvalidPasswordException;
import com.loja.useraccount.domain.port.in.LoginUseCase;
import com.loja.useraccount.domain.port.in.LogoutUseCase;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * JSF managed bean for login/logout.
 * Thin adapter: validates credentials through the login use case (which surfaces the
 * precise domain error message), then establishes the container caller identity via
 * {@link HttpServletRequest#login(String, String)} so {@code @RolesAllowed} and
 * {@code SecurityContext} checks are backed by real container authentication.
 */
@Named
@RequestScoped
public class LoginBean {

    @Inject
    private LoginUseCase loginUseCase;

    @Inject
    private LogoutUseCase logoutUseCase;

    @NotBlank(message = "E-mail is required")
    @Email(message = "Invalid e-mail format")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    public String login() {
        try {
            loginUseCase.login(email, password);
        } catch (InvalidPasswordException e) {
            addError("Login failed", e.getMessage());
            return null;
        }

        HttpServletRequest request = (HttpServletRequest) FacesContext.getCurrentInstance()
                .getExternalContext().getRequest();
        try {
            request.setAttribute(LoginAuthenticationMechanism.USERNAME_ATTRIBUTE, email);
            request.setAttribute(LoginAuthenticationMechanism.PASSWORD_ATTRIBUTE, password);
            request.login(email, password);
            request.changeSessionId();
        } catch (ServletException e) {
            addError("Login failed", "Unable to establish your session. Please try again.");
            return null;
        }
        return "/product-catalog/catalog.xhtml?faces-redirect=true";
    }

    public String logout() {
        HttpServletRequest request = (HttpServletRequest) FacesContext.getCurrentInstance()
                .getExternalContext().getRequest();
        try {
            request.logout();
        } catch (ServletException e) {
            // session invalidation below still clears the local state
        }
        logoutUseCase.logout(null);
        return "/user-account/login.xhtml?faces-redirect=true";
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
