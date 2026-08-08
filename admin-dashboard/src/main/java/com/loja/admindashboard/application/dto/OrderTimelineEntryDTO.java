package com.loja.admindashboard.application.dto;

import com.loja.ordercheckout.domain.model.OrderStatus;
import java.time.Instant;

/**
 * Presentation-friendly snapshot of one order status-timeline entry
 * (order placed, payment captured, status changed, ...).
 */
public record OrderTimelineEntryDTO(OrderStatus status, Instant occurredAt, String label) {
}
