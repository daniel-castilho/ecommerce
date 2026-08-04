package com.loja.ordercheckout.domain.port.in;

import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.domain.model.RefundRequest;
import com.loja.ordercheckout.domain.model.RefundStatus;
import com.loja.shared.domain.Money;

public interface RefundManagementUseCase {
    
    /**
     * Requests a refund for an order.
     */
    void requestRefund(String orderId, Money amount, String reason);

    /**
     * Lists refund requests, optionally filtered by status.
     */
    PageResult<RefundRequest> listRefundRequests(RefundStatus status, int page, int pageSize);

    /**
     * Approves a pending refund request, processing it through the payment gateway.
     */
    void approveRefund(String refundId);

    /**
     * Rejects a pending refund request with a reason.
     */
    void rejectRefund(String refundId, String rejectionReason);
}
