package com.loja.useraccount.adapter.in.web;

import com.loja.useraccount.application.dto.PageResult;
import com.loja.useraccount.application.dto.UserSearchCriteria;
import com.loja.useraccount.domain.model.Email;
import com.loja.useraccount.domain.model.Role;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.model.UserPassword;
import com.loja.useraccount.domain.model.UserProfile;
import com.loja.useraccount.domain.port.in.AssignRoleUseCase;
import com.loja.useraccount.domain.port.in.ListUsersUseCase;
import jakarta.el.ELContext;
import jakarta.faces.application.Application;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.component.UIViewRoot;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.context.ResponseStream;
import jakarta.faces.context.ResponseWriter;
import jakarta.faces.lifecycle.Lifecycle;
import jakarta.faces.render.RenderKit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUsersBeanTest {

    private final ListUsersUseCase listUsersUseCase = mock(ListUsersUseCase.class);
    private final AssignRoleUseCase assignRoleUseCase = mock(AssignRoleUseCase.class);

    private AdminUsersBean bean;
    private FacesContext facesContext;

    @BeforeEach
    void setUp() throws Exception {
        bean = new AdminUsersBean();
        injectField("listUsersUseCase", listUsersUseCase);
        injectField("assignRoleUseCase", assignRoleUseCase);
        facesContext = mock(FacesContext.class);
        FacesContextAccessor.setCurrent(facesContext);
    }

    @AfterEach
    void tearDown() {
        FacesContextAccessor.setCurrent(null);
    }

    static final class FacesContextAccessor extends FacesContext {
        static void setCurrent(FacesContext context) {
            setCurrentInstance(context);
        }

        @Override
        public Application getApplication() { return null; }

        @Override
        public Iterator<String> getClientIdsWithMessages() { return null; }

        @Override
        public Lifecycle getLifecycle() { return null; }

        @Override
        public ExternalContext getExternalContext() { return null; }

        @Override
        public FacesMessage.Severity getMaximumSeverity() { return null; }

        @Override
        public Iterator<FacesMessage> getMessages() { return null; }

        @Override
        public Iterator<FacesMessage> getMessages(String clientId) { return null; }

        @Override
        public RenderKit getRenderKit() { return null; }

        @Override
        public boolean getRenderResponse() { return false; }

        @Override
        public boolean getResponseComplete() { return false; }

        @Override
        public ResponseStream getResponseStream() { return null; }

        @Override
        public void setResponseStream(ResponseStream responseStream) { }

        @Override
        public ResponseWriter getResponseWriter() { return null; }

        @Override
        public void setResponseWriter(ResponseWriter responseWriter) { }

        @Override
        public UIViewRoot getViewRoot() { return null; }

        @Override
        public void setViewRoot(UIViewRoot root) { }

        @Override
        public void addMessage(String clientId, FacesMessage message) { }

        @Override
        public void release() { }

        @Override
        public void renderResponse() { }

        @Override
        public void responseComplete() { }
    }

    private void injectField(String fieldName, Object value) throws Exception {
        Field field = AdminUsersBean.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(bean, value);
    }

    private static User user(String email, Role role) {
        User u = User.create(new Email(email), UserPassword.hash("password1234", new com.loja.useraccount.domain.port.out.PasswordHasherPort() {
            @Override
            public String hash(String plainPassword) {
                return "hash:" + plainPassword;
            }

            @Override
            public boolean verify(String plainPassword, String hash) {
                return ("hash:" + plainPassword).equals(hash);
            }
        }), UserProfile.fromFullName("Admin Test"));
        u.addRole(role);
        return u;
    }

    @Test
    void shouldLoadUsersOnInit() {
        User user = user("admin@example.com", Role.ADMIN);
        when(listUsersUseCase.listUsers(anyInt(), eq(20), any(UserSearchCriteria.class)))
                .thenReturn(new PageResult<>(List.of(user), 1, 0, 20));

        bean.refresh();

        assertThat(bean.getUsers()).containsExactly(user);
        assertThat(bean.getTotalElements()).isEqualTo(1);
        assertThat(bean.getPage()).isZero();
        assertThat(bean.hasPreviousPage()).isFalse();
        assertThat(bean.hasNextPage()).isFalse();
    }

    @Test
    void shouldNavigateBetweenPages() {
        User user = user("page@example.com", Role.CUSTOMER);
        when(listUsersUseCase.listUsers(anyInt(), eq(20), any(UserSearchCriteria.class)))
                .thenReturn(new PageResult<>(List.of(user), 45, 0, 20));

        bean.refresh();
        bean.nextPage();

        assertThat(bean.getPage()).isEqualTo(1);
        assertThat(bean.hasPreviousPage()).isTrue();

        bean.previousPage();
        assertThat(bean.getPage()).isZero();
    }

    @Test
    void shouldNotAdvancePastLastPage() {
        User user = user("last@example.com", Role.CUSTOMER);
        when(listUsersUseCase.listUsers(anyInt(), eq(20), any(UserSearchCriteria.class)))
                .thenReturn(new PageResult<>(List.of(user), 20, 0, 20));

        bean.nextPage();

        assertThat(bean.getPage()).isZero();
        assertThat(bean.hasNextPage()).isFalse();
    }

    @Test
    void shouldResetPageOnSearch() {
        User user = user("search@example.com", Role.CUSTOMER);
        when(listUsersUseCase.listUsers(anyInt(), eq(20), any(UserSearchCriteria.class)))
                .thenReturn(new PageResult<>(List.of(user), 45, 0, 20));

        bean.refresh();
        bean.nextPage();
        assertThat(bean.getPage()).isEqualTo(1);

        bean.setEmailFilter("example.com");
        bean.search();

        assertThat(bean.getPage()).isZero();
        verify(listUsersUseCase, atLeastOnce()).listUsers(eq(0), eq(20), any(UserSearchCriteria.class));
    }

    @Test
    void shouldAssignRoleAndRefresh() {
        User user = user("assign@example.com", Role.CUSTOMER);
        when(listUsersUseCase.listUsers(anyInt(), eq(20), any(UserSearchCriteria.class)))
                .thenReturn(new PageResult<>(List.of(user), 1, 0, 20));

        bean.refresh();
        bean.assignRole(user, Role.ADMIN);

        verify(assignRoleUseCase).assignRole(user.getId(), Role.ADMIN);
        verify(facesContext).addMessage(eq(null), any());
    }

    @Test
    void shouldExposeFilterOptions() {
        assertThat(bean.getAvailableRoles()).contains(Role.ADMIN, Role.CUSTOMER, Role.VENDOR);
        assertThat(bean.getAvailableStatuses()).contains(com.loja.useraccount.domain.model.UserStatus.ACTIVE);
    }
}
