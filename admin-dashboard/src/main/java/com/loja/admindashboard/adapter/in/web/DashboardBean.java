package com.loja.admindashboard.adapter.in.web;

import com.loja.admindashboard.domain.port.in.DashboardMetricsUseCase;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Named
@RequestScoped
public class DashboardBean {

    @Inject
    private DashboardMetricsUseCase dashboardMetricsUseCase;

    private DashboardMetricsUseCase.DashboardSummary summary;

    public void load() {
        summary = dashboardMetricsUseCase.getSummary();
    }

    public DashboardMetricsUseCase.DashboardSummary getSummary() {
        return summary;
    }
}
