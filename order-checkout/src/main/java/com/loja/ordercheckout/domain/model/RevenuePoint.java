package com.loja.ordercheckout.domain.model;

import java.time.LocalDate;

import com.loja.shared.domain.Money;

/**
 * One point of a revenue time series: the revenue attributed to a date bucket.
 * Buckets are {@code LocalDate}s so the report layer (daily/weekly/monthly) can
 * render them without a timezone; bucketing uses the system default zone.
 */
public record RevenuePoint(LocalDate date, Money revenue) { }
