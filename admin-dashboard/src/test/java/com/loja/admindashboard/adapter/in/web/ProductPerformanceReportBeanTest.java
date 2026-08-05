package com.loja.admindashboard.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.loja.admindashboard.application.dto.ChartBar;
import com.loja.ordercheckout.domain.model.CategoryUnits;
import com.loja.ordercheckout.domain.model.ProductPerformanceReport;
import com.loja.ordercheckout.domain.model.ProductPerformanceRow;
import com.loja.ordercheckout.domain.port.in.ProductPerformanceReportUseCase;
import com.loja.productcatalog.domain.model.Category;
import com.loja.productcatalog.domain.model.Slug;
import com.loja.productcatalog.domain.port.out.CategoryRepositoryPort;
import com.loja.shared.domain.Money;

import jakarta.annotation.security.RolesAllowed;
import jakarta.faces.application.Application;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;

class ProductPerformanceReportBeanTest {

    static final class FacesContextAccessor extends FacesContext {
        static void setCurrent(FacesContext context) { setCurrentInstance(context); }
        @Override public Application getApplication() { return null; }
        @Override public ExternalContext getExternalContext() { return null; }
        @Override public void addMessage(String clientId, jakarta.faces.application.FacesMessage message) {}
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
        @Override public jakarta.faces.application.FacesMessage.Severity getMaximumSeverity() { return null; }
        @Override public java.util.Iterator<jakarta.faces.application.FacesMessage> getMessages() { return null; }
        @Override public java.util.Iterator<jakarta.faces.application.FacesMessage> getMessages(String clientId) { return null; }
        @Override public jakarta.faces.render.RenderKit getRenderKit() { return null; }
        @Override public boolean getRenderResponse() { return false; }
        @Override public boolean getResponseComplete() { return false; }
        @Override public void responseComplete() {}
    }

    private final ProductPerformanceReportUseCase useCase = mock(ProductPerformanceReportUseCase.class);
    private final CategoryRepositoryPort categoryRepository = mock(CategoryRepositoryPort.class);
    private final ProductPerformanceReportBean bean = new ProductPerformanceReportBean();

    @BeforeEach
    void setUp() {
        FacesContextAccessor.setCurrent(new FacesContextAccessor());
        bean.setProductPerformanceReportUseCase(useCase);
        bean.setCategoryRepository(categoryRepository);
    }

    @AfterEach
    void tearDown() {
        FacesContextAccessor.setCurrent(null);
    }

    @Test
    void productPerformanceReportBean_isExposedAsViewScopedAdminBean() {
        Class<ProductPerformanceReportBean> beanClass = ProductPerformanceReportBean.class;

        Named named = beanClass.getAnnotation(Named.class);
        ViewScoped viewScoped = beanClass.getAnnotation(ViewScoped.class);
        RolesAllowed rolesAllowed = beanClass.getAnnotation(RolesAllowed.class);

        assertThat(named).isNotNull();
        assertThat(named.value()).isEqualTo("productPerformanceReportBean");
        assertThat(viewScoped).isNotNull();
        assertThat(rolesAllowed).isNotNull();
        assertThat(rolesAllowed.value()).containsExactly("ADMIN");
    }

    @Test
    void load_populatesCategoriesFromRepository() {
        Category electronics = new Category(1L, "Electronics", new Slug("electronics"), null, 0, true);
        when(categoryRepository.findAllActive()).thenReturn(List.of(electronics));

        bean.load();

        assertThat(bean.getCategories()).containsExactly(electronics);
    }

    @Test
    void generate_withoutCategory_delegatesToUseCaseWithNullFilter() {
        when(useCase.productPerformanceReport(null)).thenReturn(report());

        bean.generate();

        verify(useCase).productPerformanceReport(null);
        assertThat(bean.isGenerated()).isTrue();
        assertThat(bean.getReport()).isNotNull();
    }

    @Test
    void generate_withCategory_delegatesToUseCaseWithCategoryId() {
        bean.setSelectedCategoryId(7L);
        when(useCase.productPerformanceReport(7L)).thenReturn(report());

        bean.generate();

        verify(useCase).productPerformanceReport(7L);
        assertThat(bean.isGenerated()).isTrue();
    }

    @Test
    void generate_whenUseCaseFails_doesNotMarkGenerated() {
        when(useCase.productPerformanceReport(null)).thenThrow(new RuntimeException("boom"));

        bean.generate();

        assertThat(bean.isGenerated()).isFalse();
        assertThat(bean.getTopSellers()).isEmpty();
    }

    @Test
    void displayHelpers_returnEmptyBeforeGeneration() {
        assertThat(bean.isGenerated()).isFalse();
        assertThat(bean.getTopSellers()).isEmpty();
        assertThat(bean.getTopByRevenue()).isEmpty();
        assertThat(bean.getBottomPerformers()).isEmpty();
        assertThat(bean.getUnitsByCategory()).isEmpty();
        assertThat(bean.getUnitsByCategoryChartBars()).isEmpty();
        assertThat(bean.isTopSellersEmpty()).isTrue();
        assertThat(bean.isUnitsByCategoryEmpty()).isTrue();
    }

    @Test
    void displayHelpers_formatMoneyUnitsAndChartBars() {
        when(useCase.productPerformanceReport(null)).thenReturn(report());

        bean.generate();

        assertThat(bean.getTopSellers()).extracting(ProductPerformanceRow::sku).containsExactly("SKU-1", "SKU-2");
        assertThat(bean.getTopByRevenue()).extracting(ProductPerformanceRow::sku).containsExactly("SKU-2", "SKU-1");
        assertThat(bean.getBottomPerformers()).extracting(ProductPerformanceRow::sku).containsExactly("SKU-2", "SKU-1");
        assertThat(bean.isTopSellersEmpty()).isFalse();
        assertThat(bean.isUnitsByCategoryEmpty()).isFalse();
        assertThat(bean.formatMoney(new Money(new BigDecimal("1234.50"))).replace('\u00A0', ' '))
                .isEqualTo("R$ 1.234,50");
        assertThat(bean.formatUnits(1234)).isEqualTo("1,234");

        assertThat(bean.getUnitsByCategoryChartBars())
                .extracting(ChartBar::label)
                .containsExactly("Electronics", "Home");
        assertThat(bean.getUnitsByCategoryChartBars())
                .extracting(ChartBar::title)
                .containsExactly("8", "4");
        assertThat(bean.getUnitsByCategoryChartBars())
                .extracting(ChartBar::height)
                .containsExactly(100, 50);
    }

    private static ProductPerformanceReport report() {
        return new ProductPerformanceReport(
                List.of(
                        new ProductPerformanceRow("SKU-1", "Alpha", 8L, new Money(new BigDecimal("80.00"))),
                        new ProductPerformanceRow("SKU-2", "Beta", 4L, new Money(new BigDecimal("120.00")))),
                List.of(
                        new ProductPerformanceRow("SKU-2", "Beta", 4L, new Money(new BigDecimal("120.00"))),
                        new ProductPerformanceRow("SKU-1", "Alpha", 8L, new Money(new BigDecimal("80.00")))),
                List.of(
                        new ProductPerformanceRow("SKU-2", "Beta", 4L, new Money(new BigDecimal("120.00"))),
                        new ProductPerformanceRow("SKU-1", "Alpha", 8L, new Money(new BigDecimal("80.00")))),
                List.of(
                        new CategoryUnits("Electronics", 8L),
                        new CategoryUnits("Home", 4L)));
    }
}
