package com.loja.useraccount.adapter.in.web;

import com.loja.useraccount.adapter.session.CurrentUser;
import com.loja.useraccount.domain.exception.InvalidPasswordException;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.port.in.ChangePasswordUseCase;
import com.loja.useraccount.domain.port.in.UpdateProfileUseCase;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * JSF managed bean for user profile management.
 * Thin adapter: delegates to UpdateProfileUseCase and ChangePasswordUseCase (SRP).
 */
@Named
@RequestScoped
public class ProfileBean {

    @Inject
    @CurrentUser
    private User currentUser;

    @Inject
    private UpdateProfileUseCase updateProfileUseCase;

    @Inject
    private ChangePasswordUseCase changePasswordUseCase;

    @NotBlank(message = "Full name is required")
    @Size(max = 200, message = "Full name must be at most 200 characters")
    private String fullName;

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    private String newPassword;

    public void saveProfile() {
        try {
            updateProfileUseCase.updateProfile(currentUser.getId(), fullName);
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO, "Success", "Profile updated"));
        } catch (IllegalArgumentException e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error", e.getMessage()));
        }
    }

    public void changePassword() {
        try {
            changePasswordUseCase.changePassword(currentUser.getId(), currentPassword, newPassword);
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO, "Success", "Password changed"));
        } catch (InvalidPasswordException | IllegalArgumentException e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error", e.getMessage()));
        }
    }

    /** Initialise the form with the current user's name. */
    public String getFullName() {
        return (fullName != null) ? fullName : (currentUser != null ? currentUser.getFullName() : "");
    }

    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
