package com.loja.useraccount.adapter.in.web;

import com.loja.useraccount.application.dto.PageResult;
import com.loja.useraccount.application.dto.UserSearchCriteria;
import com.loja.useraccount.domain.model.Role;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.model.UserStatus;
import com.loja.useraccount.domain.port.in.AssignRoleUseCase;
import com.loja.useraccount.domain.port.in.ListUsersUseCase;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.List;

/**
 * Admin-only JSF bean for user management (listing, filtering, role assignment).
 * Thin adapter: delegates to ListUsersUseCase and AssignRoleUseCase (SRP).
 */
@Named
@ViewScoped
@RolesAllowed("ADMIN")
public class AdminUsersBean implements Serializable {

    private static final int PAGE_SIZE = 20;

    @Inject
    private ListUsersUseCase listUsersUseCase;

    @Inject
    private AssignRoleUseCase assignRoleUseCase;

    private List<User> users;
    private long totalElements;
    private int page;

    private String emailFilter;
    private UserStatus statusFilter;
    private Role selectedRole;

    @PostConstruct
    void loadUsers() {
        refresh();
    }

    public void refresh() {
        UserSearchCriteria criteria = new UserSearchCriteria(emailFilter, statusFilter, "email");
        PageResult<User> result = listUsersUseCase.listUsers(page, PAGE_SIZE, criteria);
        users = result.items();
        totalElements = result.totalElements();
    }

    public void search() {
        page = 0;
        refresh();
    }

    public void nextPage() {
        if ((page + 1) * PAGE_SIZE < totalElements) {
            page++;
            refresh();
        }
    }

    public void previousPage() {
        if (page > 0) {
            page--;
            refresh();
        }
    }

    public void assignRole(User user, Role role) {
        assignRoleUseCase.assignRole(user.getId(), role);
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO, "Role assigned",
                        user.getEmail() + " is now " + role));
        refresh();
    }

    public boolean hasPreviousPage() { return page > 0; }
    public boolean hasNextPage() { return (page + 1) * PAGE_SIZE < totalElements; }

    public List<User> getUsers() { return users; }
    public long getTotalElements() { return totalElements; }
    public int getPage() { return page; }
    public Role[] getAvailableRoles() { return Role.values(); }
    public UserStatus[] getAvailableStatuses() { return UserStatus.values(); }

    public String getEmailFilter() { return emailFilter; }
    public void setEmailFilter(String emailFilter) { this.emailFilter = emailFilter; }
    public UserStatus getStatusFilter() { return statusFilter; }
    public void setStatusFilter(UserStatus statusFilter) { this.statusFilter = statusFilter; }
    public Role getSelectedRole() { return selectedRole; }
    public void setSelectedRole(Role selectedRole) { this.selectedRole = selectedRole; }
}
