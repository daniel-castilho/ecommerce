package com.loja.ordercheckout.domain.model;

import java.time.LocalDate;

/**
 * One point of the new-customers-per-day series of the customer insights report
 * (admin reporting, backlog S22). Mirrors {@code UserGrowthPoint} from the
 * user-account module; kept local so the report model has no cross-module
 * domain dependency.
 *
 * @param date  registration date (bucket key).
 * @param count number of accounts registered on {@code date}.
 */
public record CustomerGrowthPoint(LocalDate date, long count) {
}
