package com.loja.admindashboard.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.loja.admindashboard.application.dto.OrderDetailsDTO;
import com.loja.admindashboard.application.dto.OrderItemDTO;
import com.loja.admindashboard.application.dto.OrderTimelineEntryDTO;
import com.loja.admindashboard.application.dto.PaymentTransactionDTO;
import com.loja.admindashboard.application.dto.ShippingAddressDTO;
import com.loja.admindashboard.domain.port.in.GetOrderDetailsUseCase;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderLine;
import com.loja.ordercheckout.domain.model.PaymentInfo;
import com.loja.ordercheckout.domain.model.RefundRequest;
import com.loja.ordercheckout.domain.model.RefundStatus;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import com.loja.ordercheckout.domain.port.out.RefundRequestRepositoryPort;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Application service for the admin order-detail flow. Composes the persisted
 * order aggregate (including its status timeline) with the payment snapshot and
 * the refund requests for that order into a presentation-friendly DTO.
 */
@ApplicationScoped
public class GetOrderDetailsService implements GetOrderDetailsUseCase {

    private static final Map<RefundStatus, String> REFUND_TYPES = new EnumMap<>(RefundStatus.class);

    static {
        REFUND_TYPES.put(RefundStatus.PENDING, "REFUND_REQUESTED");
        REFUND_TYPES.put(RefundStatus.APPROVED, "REFUND_APPROVED");
        REFUND_TYPES.put(RefundStatus.PROCESSED, "REFUND_PROCESSED");
        REFUND_TYPES.put(RefundStatus.REJECTED, "REFUND_REJECTED");
    }

    private final OrderRepositoryPort orderRepository;
    private final RefundRequestRepositoryPort refundRequestRepository;

    @Inject
    public GetOrderDetailsService(OrderRepositoryPort orderRepository,
                                  RefundRequestRepositoryPort refundRequestRepository) {
        this.orderRepository = orderRepository;
        this.refundRequestRepository = refundRequestRepository;
    }

    @Override
    public Optional<OrderDetailsDTO> findById(String orderId) {
        return orderRepository.findById(orderId).map(this::toOrderDetailsDTO);
    }

    private OrderDetailsDTO toOrderDetailsDTO(Order order) {
        List<PaymentTransactionDTO> payments = buildPaymentTransactions(order);
        return new OrderDetailsDTO(
                order.getId(),
                order.getStatus(),
                order.getCustomerEmail(),
                order.getCreatedAt(),
                toShippingAddressDTO(order.getShippingAddress()),
                toOrderItemDTOs(order.getItems()),
                order.getShippingCost(),
                order.getTotal(),
                order.getTrackingNumber(),
                toTimelineDTOs(order),
                payments
        );
    }

    private List<OrderTimelineEntryDTO> toTimelineDTOs(Order order) {
        List<OrderTimelineEntryDTO> entries = order.getTimeline().stream()
                .map(entry -> new OrderTimelineEntryDTO(entry.status(), entry.occurredAt(), entry.label()))
                .collect(Collectors.toCollection(ArrayList::new));
        if (entries.isEmpty()) {
            entries.add(new OrderTimelineEntryDTO(order.getStatus(), order.getCreatedAt(), "Order placed"));
        }
        return entries;
    }

    private List<PaymentTransactionDTO> buildPaymentTransactions(Order order) {
        List<PaymentTransactionDTO> transactions = new ArrayList<>();
        PaymentInfo payment = order.getPaymentInfo();
        if (payment != null) {
            if (payment.getAuthorizationId() != null) {
                transactions.add(new PaymentTransactionDTO("AUTHORIZATION",
                        payment.getAuthorizedAmount(), payment.getAuthorizationTime(),
                        payment.getGatewayTransactionId()));
            }
            if (payment.getCaptureId() != null) {
                transactions.add(new PaymentTransactionDTO("CAPTURE",
                        payment.getCapturedAmount(), payment.getCaptureTime(),
                        payment.getGatewayTransactionId()));
            }
        }
        for (RefundRequest refund : refundRequestRepository.findByOrderId(order.getId())) {
            String type = REFUND_TYPES.get(refund.getStatus());
            Instant occurredAt = refund.getProcessedAt() != null
                    ? refund.getProcessedAt()
                    : refund.getCreatedAt();
            transactions.add(new PaymentTransactionDTO(type, refund.getAmount(), occurredAt,
                    refund.getReason()));
        }
        return transactions;
    }

    private ShippingAddressDTO toShippingAddressDTO(com.loja.ordercheckout.domain.model.ShippingAddress address) {
        if (address == null) {
            return null;
        }
        return new ShippingAddressDTO(
                address.getRecipientName(),
                address.getStreet(),
                address.getNumber(),
                address.getComplement(),
                address.getNeighborhood(),
                address.getCity(),
                address.getState(),
                address.getPostalCode()
        );
    }

    private List<OrderItemDTO> toOrderItemDTOs(List<OrderLine> items) {
        return items.stream()
                .map(this::toOrderItemDTO)
                .collect(Collectors.toList());
    }

    private OrderItemDTO toOrderItemDTO(OrderLine item) {
        return new OrderItemDTO(
                item.getProductName(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getLineTotal()
        );
    }
}
