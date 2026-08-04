package com.loja.admindashboard.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.domain.port.in.CustomerOrderHistoryUseCase;
import com.loja.useraccount.domain.model.Email;
import com.loja.useraccount.domain.model.User;
import com.loja.useraccount.domain.model.UserPassword;
import com.loja.useraccount.domain.model.UserProfile;
import com.loja.useraccount.domain.port.in.ChangeUserStatusUseCase;
import com.loja.useraccount.domain.port.in.FindUserUseCase;
import com.loja.useraccount.domain.port.out.PasswordHasherPort;

import jakarta.annotation.security.RolesAllowed;
import jakarta.faces.application.Application;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;

class AdminCustomerDetailBeanTest {

    static final class FacesContextAccessor extends FacesContext {
        static void setCurrent(FacesContext context) { setCurrentInstance(context); }
        @Override public Application getApplication() { return null; }
        @Override public ExternalContext getExternalContext() { return null; }
        @Override public void addMessage(String clientId, FacesMessage message) {}
        @Override public void release() {}
        @Override public jakarta.faces.context.ResponseStream getResponseStream() { return null; }
        @Override public void setResponseStream(jakarta.faces.context.ResponseStream responseStream) {}
        @Override public jakarta.faces.context.ResponseWriter getResponseWriter() { return null; }
        @Override public void setResponseWriter(jakarta.faces.context.ResponseWriter responseWriter) {}
        @Override public jakarta.faces.component.UIViewRoot getViewRoot() { return null; }
        @Override public void setViewRoot(jakarta.faces.component.UIViewRoot root) {}
        @Override public void renderResponse() {}
        @Override public jakarta.faces.lifecycle.Lifecycle getLifecycle() { return null; }
        @Override public java.util.Iterator<String> getClientIdsWithMessages() { return null; }
        @Override public FacesMessage.Severity getMaximumSeverity() { return null; }
        @Override public java.util.Iterator<FacesMessage> getMessages() { return null; }
        @Override public java.util.Iterator<FacesMessage> getMessages(String clientId) { return null; }
        @Override public jakarta.faces.render.RenderKit getRenderKit() { return null; }
        @Override public boolean getRenderResponse() { return false; }
        @Override public boolean getResponseComplete() { return false; }
        @Override public void responseComplete() {}
    }

    private FindUserUseCase findUserUseCase;
    private CustomerOrderHistoryUseCase customerOrderHistoryUseCase;
    private ChangeUserStatusUseCase changeUserStatusUseCase;
    private AdminCustomerDetailBean bean;

    @BeforeEach
    void setUp() {
        FacesContextAccessor.setCurrent(new FacesContextAccessor());
        findUserUseCase = mock(FindUserUseCase.class);
        customerOrderHistoryUseCase = mock(CustomerOrderHistoryUseCase.class);
        changeUserStatusUseCase = mock(ChangeUserStatusUseCase.class);
        bean = new AdminCustomerDetailBean();
        bean.setFindUserUseCase(findUserUseCase);
        bean.setCustomerOrderHistoryUseCase(customerOrderHistoryUseCase);
        bean.setChangeUserStatusUseCase(changeUserStatusUseCase);
    }

    @AfterEach
    void tearDown() {
        FacesContextAccessor.setCurrent(null);
    }

    @Test
    void customerDetailBean_isExposedAsViewScopedAdminBean() {
        Class<AdminCustomerDetailBean> beanClass = AdminCustomerDetailBean.class;

        Named named = beanClass.getAnnotation(Named.class);
        ViewScoped viewScoped = beanClass.getAnnotation(ViewScoped.class);
        RolesAllowed rolesAllowed = beanClass.getAnnotation(RolesAllowed.class);

        assertThat(named).isNotNull();
        assertThat(named.value()).isEqualTo("adminCustomerDetailBean");
        assertThat(viewScoped).isNotNull();
        assertThat(rolesAllowed).isNotNull();
        assertThat(rolesAllowed.value()).containsExactly("ADMIN");
    }

    @Test
    void loadCustomer_populatesCustomerAndRecentOrders() {
        User user = user();
        when(findUserUseCase.findById("u-1")).thenReturn(Optional.of(user));
        when(customerOrderHistoryUseCase.listByCustomer("u-1", 0, 5))
                .thenReturn(new PageResult<>(List.of(), 0L, 0, 5));

        bean.setCustomerId("u-1");
        bean.loadCustomer();

        assertThat(bean.getCustomer()).isEqualTo(user);
        assertThat(bean.getRecentOrders()).isEmpty();
    }

    @Test
    void block_delegatesToUseCaseAndReloads() {
        User user = user();
        when(findUserUseCase.findById("u-1")).thenReturn(Optional.of(user));
        when(customerOrderHistoryUseCase.listByCustomer("u-1", 0, 5))
                .thenReturn(new PageResult<>(List.of(), 0L, 0, 5));

        bean.setCustomerId("u-1");
        bean.loadCustomer();
        bean.block();

        verify(changeUserStatusUseCase).blockUser("u-1");
    }

    @Test
    void unblock_delegatesToUseCaseAndReloads() {
        User user = user();
        when(findUserUseCase.findById("u-1")).thenReturn(Optional.of(user));
        when(customerOrderHistoryUseCase.listByCustomer("u-1", 0, 5))
                .thenReturn(new PageResult<>(List.of(), 0L, 0, 5));

        bean.setCustomerId("u-1");
        bean.loadCustomer();
        bean.unblock();

        verify(changeUserStatusUseCase).unblockUser("u-1");
    }

    @Test
    void block_whenCustomerNotLoaded_doesNothing() {
        when(findUserUseCase.findById("u-1")).thenReturn(Optional.empty());

        bean.setCustomerId("u-1");
        bean.loadCustomer();
        bean.block();
        bean.unblock();

        verifyNoInteractions(changeUserStatusUseCase);
    }

    @Test
    void isBlocked_reflectsInactiveOrLockedStatus() {
        User user = user();
        when(findUserUseCase.findById("u-1")).thenReturn(Optional.of(user));
        when(customerOrderHistoryUseCase.listByCustomer("u-1", 0, 5))
                .thenReturn(new PageResult<>(List.of(), 0L, 0, 5));

        bean.setCustomerId("u-1");
        bean.loadCustomer();
        assertThat(bean.isBlocked()).isFalse();

        user.deactivate();
        assertThat(bean.isBlocked()).isTrue();

        user.activate();
        assertThat(bean.isBlocked()).isFalse();
    }

    private static User user() {
        PasswordHasherPort hasher = mock(PasswordHasherPort.class);
        Email email = Email.tryCreate("john@example.com").getValue().get();
        UserProfile profile = UserProfile.tryFromFullName("John Doe").getValue().get();
        UserPassword passwordHash = UserPassword.tryHash("Str0ng!Pass", hasher).getValue().get();
        return new User("u-1", email, passwordHash, profile);
    }
}
