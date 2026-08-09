package com.loja.ordercheckout.domain.port.in;

import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.application.dto.RefundSearchCriteria;
import com.loja.ordercheckout.domain.model.RefundRequest;
import com.loja.shared.domain.Money;
import java.util.Optional;

public interface RefundManagementUseCase {
    
    /**
     * Requests a refund for an order.
     */
    void requestRefund(String orderId, Money amount, String reason);

    /**
     * Lists refund requests, optionally filtered by status, customer, date range
     * and sort order (see {@link RefundSearchCriteria}).
     */
    PageResult<RefundRequest> listRefundRequests(RefundSearchCriteria criteria, int page, int pageSize);

    /**
     * Finds a single refund request by id (admin detail view).
     */
    Optional<RefundRequest> findRefundById(String refundId);

    /**
     * Approves a pending refund request, processing it through the payment gateway.
     */
    void approveRefund(String refundId);

    /**
     * Rejects a pending refund request with a reason.
     */
    void rejectRefund(String refundId, String rejectionReason);
}
