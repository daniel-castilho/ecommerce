package com.loja.ordercheckout.domain.model;

/**
 * Bucket granularity for the revenue report time series. DAILY is the native
 * granularity of the repository query; WEEKLY/MONTHLY roll the daily series up
 * (week = ISO week starting Monday, month = first day of month).
 */
public enum ReportGranularity {
    DAILY,
    WEEKLY,
    MONTHLY
}
