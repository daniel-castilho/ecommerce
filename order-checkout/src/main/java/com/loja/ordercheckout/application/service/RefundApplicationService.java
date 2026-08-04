package com.loja.ordercheckout.application.service;

import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.domain.exception.OrderNotFoundException;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderStatus;
import com.loja.ordercheckout.domain.model.RefundRequest;
import com.loja.ordercheckout.domain.model.RefundStatus;
import com.loja.ordercheckout.domain.port.in.RefundManagementUseCase;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;
import com.loja.ordercheckout.domain.port.out.PaymentGatewayPort;
import com.loja.ordercheckout.domain.port.out.RefundRequestRepositoryPort;
import com.loja.shared.domain.Money;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
@Transactional
public class RefundApplicationService implements RefundManagementUseCase {

    private final RefundRequestRepositoryPort refundRepository;
    private final OrderRepositoryPort orderRepository;
    private final PaymentGatewayPort paymentGateway;

    @Inject
    public RefundApplicationService(RefundRequestRepositoryPort refundRepository,
                                    OrderRepositoryPort orderRepository,
                                    PaymentGatewayPort paymentGateway) {
        this.refundRepository = refundRepository;
        this.orderRepository = orderRepository;
        this.paymentGateway = paymentGateway;
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
    public void approveRefund(String refundId) {
        RefundRequest request = refundRepository.findById(refundId)
                .orElseThrow(() -> new IllegalArgumentException("Refund request not found: " + refundId));
        
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new OrderNotFoundException(request.getOrderId()));

        request.approve();
        refundRepository.save(request);

        boolean success = paymentGateway.processRefund(request);
        if (success) {
            request.markAsProcessed();
            order.updateStatus(OrderStatus.REFUNDED);
        } else {
            // Ideally we would set it back to PENDING or a FAILED state
            // For now, we will just throw an exception to rollback
            throw new RuntimeException("Payment Gateway failed to process refund.");
        }

        refundRepository.save(request);
        orderRepository.save(order);
    }

    @Override
    public void rejectRefund(String refundId, String rejectionReason) {
        RefundRequest request = refundRepository.findById(refundId)
                .orElseThrow(() -> new IllegalArgumentException("Refund request not found: " + refundId));

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new OrderNotFoundException(request.getOrderId()));

        request.reject(rejectionReason);
        refundRepository.save(request);

        // Put order back to DELIVERED or previous status if needed
        // For simplicity, we just mark it as DELIVERED if it was rejected
        order.updateStatus(OrderStatus.DELIVERED);
        orderRepository.save(order);
    }
}
