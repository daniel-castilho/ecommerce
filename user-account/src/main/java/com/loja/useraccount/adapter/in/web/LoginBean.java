package com.loja.useraccount.adapter.in.web;

import com.loja.useraccount.domain.exception.InvalidPasswordException;
import com.loja.useraccount.domain.port.in.LoginUseCase;
import com.loja.useraccount.domain.port.in.LogoutUseCase;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * JSF managed bean for login/logout.
 * Thin adapter: translates form input into use case calls, no business logic here (SRP).
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
            return "/product-catalog/catalog.xhtml?faces-redirect=true";
        } catch (InvalidPasswordException e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Login failed", e.getMessage()));
            return null;
        }
    }

    public String logout() {
        // userId resolved from session inside the use case via SessionPort
        logoutUseCase.logout(null);
        return "/user-account/login.xhtml?faces-redirect=true";
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
