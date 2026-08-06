package com.loja.useraccount.domain.model;

import java.time.LocalDate;

/**
 * One point of the new-customers-per-day series (admin reporting, backlog S22).
 * The date is bucketed using the system default zone; the count is the number
 * of accounts registered on that date.
 *
 * @param date  registration date (bucket key).
 * @param count number of accounts registered on {@code date}.
 */
public record UserGrowthPoint(LocalDate date, long count) {
}
