package com.loja.admindashboard.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.loja.admindashboard.domain.port.in.UpdateProductForAdminUseCase;
import com.loja.productcatalog.domain.model.Product;
import com.loja.productcatalog.domain.model.ProductStatus;
import com.loja.productcatalog.domain.model.Sku;
import com.loja.productcatalog.domain.model.Slug;
import com.loja.productcatalog.domain.port.in.ActivateProductUseCase;
import com.loja.productcatalog.domain.port.in.ArchiveProductUseCase;
import com.loja.shared.domain.Money;

import jakarta.faces.application.Application;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.context.Flash;

class ProductEditBeanTest {

    @Test
    void save_updatesExistingProductAndRedirectsToList() {
        UpdateProductForAdminUseCase updateProductForAdminUseCase = mock(UpdateProductForAdminUseCase.class);
        ProductEditBean bean = new ProductEditBean();
        bean.setUpdateProductForAdminUseCase(updateProductForAdminUseCase);
        bean.setProductId("p-42");
        bean.setName("Updated Keyboard");
        bean.setSlug("updated-keyboard");
        bean.setShortDescription("Updated description");
        bean.setDescription("New keyboard description");
        bean.setStock(24);
        bean.setWeightGrams(950);
        bean.setMetaTitle("Keyboard 2026");
        bean.setMetaDescription("Updated meta");
        bean.setSelectedCategoryIds(Set.of(9L, 11L));

        Product updated = new Product("p-42", new Sku("SKU-42"), new Slug("updated-keyboard"),
                "Updated Keyboard", "Updated description", "New keyboard description",
                new Money(new BigDecimal("189.90")), null, 24, ProductStatus.ACTIVE,
                950, "Keyboard 2026", "Updated meta", Set.of(9L, 11L), List.of());
        when(updateProductForAdminUseCase.update(eq("p-42"), argThat(command ->
                command.name().equals("Updated Keyboard")
                        && command.slug().equals("updated-keyboard")
                        && command.stock() == 24
                        && command.categoryIds().equals(Set.of(9L, 11L)))))
                .thenReturn(updated);

        FacesContext facesContext = mock(FacesContext.class);
        ExternalContext externalContext = mock(ExternalContext.class);
        Flash flash = mock(Flash.class);
        when(facesContext.getExternalContext()).thenReturn(externalContext);
        when(externalContext.getFlash()).thenReturn(flash);
        FacesContextAccessor.setCurrent(facesContext);

        String outcome = bean.submit();

        assertThat(outcome).isEqualTo("/admin-dashboard/products/list.xhtml?faces-redirect=true");
        verify(updateProductForAdminUseCase).update(eq("p-42"), argThat(command ->
                command.name().equals("Updated Keyboard")
                        && command.slug().equals("updated-keyboard")
                        && command.stock() == 24
                        && command.categoryIds().equals(Set.of(9L, 11L))));
        verify(flash).setKeepMessages(true);

        FacesContextAccessor.setCurrent(null);
    }

    @Test
    void activate_archivedProduct_callsUseCaseAndRedirectsToList() {
        ActivateProductUseCase activateProductUseCase = mock(ActivateProductUseCase.class);
        ProductEditBean bean = new ProductEditBean();
        bean.setActivateProductUseCase(activateProductUseCase);
        bean.setProductId("p-42");
        bean.setCurrentProduct(new Product("p-42", new Sku("SKU-42"), new Slug("slug"),
                "Keyboard", "Short", "Desc", new Money(new BigDecimal("100.00")), null,
                10, ProductStatus.ARCHIVED, 500, "meta", "meta", Set.of(1L), List.of()));

        FacesContext facesContext = mock(FacesContext.class);
        Application application = mock(Application.class);
        ExternalContext externalContext = mock(ExternalContext.class);
        Flash flash = mock(Flash.class);
        ResourceBundle bundle = new ResourceBundle() {
            @Override
            protected Object handleGetObject(String key) {
                return key;
            }

            @Override
            public Enumeration<String> getKeys() {
                return Collections.enumeration(Set.of("product.activated", "product.activate.failed"));
            }
        };
        when(application.getResourceBundle(facesContext, "msg")).thenReturn(bundle);
        when(facesContext.getApplication()).thenReturn(application);
        when(facesContext.getExternalContext()).thenReturn(externalContext);
        when(externalContext.getFlash()).thenReturn(flash);

        FacesContextAccessor.setCurrent(facesContext);

        String outcome = bean.activate();

        assertThat(outcome).isEqualTo("/admin-dashboard/products/list.xhtml?faces-redirect=true");
        verify(activateProductUseCase).activate("p-42");
        verify(flash).setKeepMessages(true);
        verify(facesContext).addMessage(eq(null), any(FacesMessage.class));

        FacesContextAccessor.setCurrent(null);
    }

    @Test
    void deactivate_activeProduct_callsUseCaseAndRedirectsToList() {
        ArchiveProductUseCase archiveProductUseCase = mock(ArchiveProductUseCase.class);
        ProductEditBean bean = new ProductEditBean();
        bean.setArchiveProductUseCase(archiveProductUseCase);
        bean.setProductId("p-42");
        bean.setCurrentProduct(new Product("p-42", new Sku("SKU-42"), new Slug("slug"),
                "Keyboard", "Short", "Desc", new Money(new BigDecimal("100.00")), null,
                10, ProductStatus.ACTIVE, 500, "meta", "meta", Set.of(1L), List.of()));

        FacesContext facesContext = mock(FacesContext.class);
        Application application = mock(Application.class);
        ExternalContext externalContext = mock(ExternalContext.class);
        Flash flash = mock(Flash.class);
        ResourceBundle bundle = new ResourceBundle() {
            @Override
            protected Object handleGetObject(String key) {
                return key;
            }

            @Override
            public Enumeration<String> getKeys() {
                return Collections.enumeration(Set.of("product.deactivated", "product.deactivate.failed"));
            }
        };
        when(application.getResourceBundle(facesContext, "msg")).thenReturn(bundle);
        when(facesContext.getApplication()).thenReturn(application);
        when(facesContext.getExternalContext()).thenReturn(externalContext);
        when(externalContext.getFlash()).thenReturn(flash);

        FacesContextAccessor.setCurrent(facesContext);

        String outcome = bean.deactivate();

        assertThat(outcome).isEqualTo("/admin-dashboard/products/list.xhtml?faces-redirect=true");
        verify(archiveProductUseCase).archive("p-42");
        verify(flash).setKeepMessages(true);
        verify(facesContext).addMessage(eq(null), any(FacesMessage.class));

        FacesContextAccessor.setCurrent(null);
    }

    static final class FacesContextAccessor extends FacesContext {
        static void setCurrent(FacesContext context) {
            setCurrentInstance(context);
        }

        @Override
        public Application getApplication() {
            return null;
        }

        @Override
        public ExternalContext getExternalContext() {
            return null;
        }

        @Override
        public void addMessage(String clientId, FacesMessage message) {
        }

        @Override
        public void release() {
        }

        @Override
        public jakarta.faces.context.ResponseStream getResponseStream() {
            return null;
        }

        @Override
        public void setResponseStream(jakarta.faces.context.ResponseStream responseStream) {
        }

        @Override
        public jakarta.faces.context.ResponseWriter getResponseWriter() {
            return null;
        }

        @Override
        public void setResponseWriter(jakarta.faces.context.ResponseWriter responseWriter) {
        }

        @Override
        public jakarta.faces.component.UIViewRoot getViewRoot() {
            return null;
        }

        @Override
        public void setViewRoot(jakarta.faces.component.UIViewRoot root) {
        }

        @Override
        public void renderResponse() {
        }

        @Override
        public jakarta.faces.lifecycle.Lifecycle getLifecycle() {
            return null;
        }

        @Override
        public Iterator<String> getClientIdsWithMessages() {
            return null;
        }

        @Override
        public FacesMessage.Severity getMaximumSeverity() {
            return null;
        }

        @Override
        public Iterator<FacesMessage> getMessages() {
            return null;
        }

        @Override
        public Iterator<FacesMessage> getMessages(String clientId) {
            return null;
        }

        @Override
        public jakarta.faces.render.RenderKit getRenderKit() {
            return null;
        }

        @Override
        public boolean getRenderResponse() {
            return false;
        }

        @Override
        public boolean getResponseComplete() {
            return false;
        }

        @Override
        public void responseComplete() {
        }
    }
}
