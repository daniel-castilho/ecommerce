package com.loja.useraccount.adapter.in.web;

import com.loja.useraccount.domain.exception.InvalidPasswordException;
import com.loja.useraccount.domain.port.in.RequestPasswordResetUseCase;
import com.loja.useraccount.domain.port.in.ResetPasswordUseCase;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * JSF managed bean for the password reset flow.
 * Covers both the request step (email form) and the confirmation step (new password).
 * Thin adapter: delegates to the use cases, no business logic here (SRP).
 */
@Named
@RequestScoped
public class PasswordResetBean {

    @Inject
    private RequestPasswordResetUseCase requestPasswordResetUseCase;

    @Inject
    private ResetPasswordUseCase resetPasswordUseCase;

    @NotBlank(message = "E-mail is required")
    @Email(message = "Invalid e-mail format")
    private String email;

    private String token;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    private String newPassword;

    @PostConstruct
    void init() {
        String requestToken = FacesContext.getCurrentInstance()
                .getExternalContext().getRequestParameterMap().get("token");
        if (requestToken != null && !requestToken.isBlank()) {
            token = requestToken;
        }
    }

    public String requestReset() {
        requestPasswordResetUseCase.requestPasswordReset(email);
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO, "Request received",
                        "If an account exists for that email, a reset link has been sent."));
        return null;
    }

    public String resetPassword() {
        try {
            resetPasswordUseCase.resetPassword(token, newPassword);
            return "/user-account/login.xhtml?faces-redirect=true";
        } catch (InvalidPasswordException | IllegalArgumentException e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Reset failed", e.getMessage()));
            return null;
        }
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
