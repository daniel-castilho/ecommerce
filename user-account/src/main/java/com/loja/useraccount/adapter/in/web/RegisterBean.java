package com.loja.useraccount.adapter.in.web;

import com.loja.useraccount.domain.exception.EmailAlreadyRegisteredException;
import com.loja.useraccount.domain.port.in.RegisterUserUseCase;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * JSF managed bean for user registration.
 * Thin adapter: delegates directly to RegisterUserUseCase, no business logic here (SRP).
 */
@Named
@RequestScoped
public class RegisterBean {

    @Inject
    private RegisterUserUseCase registerUserUseCase;

    @NotBlank(message = "Full name is required")
    @Size(max = 200, message = "Full name must be at most 200 characters")
    private String fullName;

    @NotBlank(message = "E-mail is required")
    @Email(message = "Invalid e-mail format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    private String password;

    public String register() {
        try {
            registerUserUseCase.register(email, password, fullName);
            return "/user-account/login.xhtml?faces-redirect=true";
        } catch (EmailAlreadyRegisteredException | IllegalArgumentException e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Registration failed", e.getMessage()));
            return null;
        }
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
}
