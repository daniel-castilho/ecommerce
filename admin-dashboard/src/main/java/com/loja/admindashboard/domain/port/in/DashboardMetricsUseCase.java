package com.loja.admindashboard.domain.port.in;

import com.loja.admindashboard.application.dto.DashboardSummaryDTO;

public interface DashboardMetricsUseCase {
    DashboardSummaryDTO getSummary();
}
