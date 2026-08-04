package com.loja.ordercheckout.application.service;

import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.domain.exception.OrderNotFoundException;
import com.loja.ordercheckout.domain.exception.PaymentFailedException;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderStatus;
import com.loja.ordercheckout.domain.model.RefundRequest;
import com.loja.ordercheckout.domain.model.RefundStatus;
import com.loja.ordercheckout.domain.port.in.RefundManagementUseCase;
import com.loja.ordercheckout.domain.port.out.NotificationPort;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import com.loja.ordercheckout.domain.port.out.PaymentGatewayPort;
import com.loja.ordercheckout.domain.port.out.RefundRequestRepositoryPort;
import com.loja.shared.domain.Money;
import com.loja.shared.event.DomainEventPublisherPort;
import com.loja.shared.event.RefundProcessedEvent;
import com.loja.shared.event.RefundRejectedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
@Transactional
public class RefundApplicationService implements RefundManagementUseCase {

    private final RefundRequestRepositoryPort refundRepository;
    private final OrderRepositoryPort orderRepository;
    private final PaymentGatewayPort paymentGateway;
    private final NotificationPort notificationPort;
    private final DomainEventPublisherPort eventPublisher;

    @Inject
    public RefundApplicationService(RefundRequestRepositoryPort refundRepository,
                                    OrderRepositoryPort orderRepository,
                                    PaymentGatewayPort paymentGateway,
                                    NotificationPort notificationPort,
                                    DomainEventPublisherPort eventPublisher) {
        this.refundRepository = refundRepository;
        this.orderRepository = orderRepository;
        this.paymentGateway = paymentGateway;
        this.notificationPort = notificationPort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void requestRefund(String orderId, Money amount, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        
        RefundRequest request = RefundRequest.request(orderId, amount, reason);
        refundRepository.save(request);

        order.updateStatus(OrderStatus.REFUND_REQUESTED);
        orderRepository.save(order);
    }

    @Override
    public PageResult<RefundRequest> listRefundRequests(RefundStatus status, int page, int pageSize) {
        if (status == null) {
            return refundRepository.findAll(page, pageSize);
        }
        return refundRepository.findByStatus(status, page, pageSize);
    }

    @Override
    public Optional<RefundRequest> findRefundById(String refundId) {
        return refundRepository.findById(refundId);
    }

    @Override
    public void approveRefund(String refundId) {
        RefundRequest request = refundRepository.findById(refundId)
                .orElseThrow(() -> new IllegalArgumentException("Refund request not found: " + refundId));

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new OrderNotFoundException(request.getOrderId()));

        request.approve();
        refundRepository.save(request);

        boolean success = paymentGateway.processRefund(request);
        if (!success) {
            // The transaction rolls back, so the persisted refund stays PENDING.
            throw new PaymentFailedException(
                    "Payment gateway failed to process refund for request " + refundId);
        }

        request.markAsProcessed();
        refundRepository.save(request);

        order.updateStatus(OrderStatus.REFUNDED);
        orderRepository.save(order);

        notificationPort.notifyRefundApproved(order, request);
        eventPublisher.publish(new RefundProcessedEvent(request.getId(), request.getOrderId(), Instant.now()));
    }

    @Override
    public void rejectRefund(String refundId, String rejectionReason) {
        RefundRequest request = refundRepository.findById(refundId)
                .orElseThrow(() -> new IllegalArgumentException("Refund request not found: " + refundId));

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new OrderNotFoundException(request.getOrderId()));

        request.reject(rejectionReason);
        refundRepository.save(request);

        // Put the order back to DELIVERED since the refund was declined.
        order.updateStatus(OrderStatus.DELIVERED);
        orderRepository.save(order);

        notificationPort.notifyRefundRejected(order, request);
        eventPublisher.publish(new RefundRejectedEvent(request.getId(), request.getOrderId(), rejectionReason, Instant.now()));
    }
}
