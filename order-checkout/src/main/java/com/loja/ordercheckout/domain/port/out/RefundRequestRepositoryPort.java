package com.loja.ordercheckout.domain.port.out;

import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.domain.model.RefundRequest;
import com.loja.ordercheckout.domain.model.RefundStatus;
import java.util.List;
import java.util.Optional;

public interface RefundRequestRepositoryPort {
    void save(RefundRequest request);
    Optional<RefundRequest> findById(String id);
    PageResult<RefundRequest> findAll(int page, int pageSize);
    PageResult<RefundRequest> findByStatus(RefundStatus status, int page, int pageSize);

    /** All refund requests for an order, newest first (admin order detail). */
    List<RefundRequest> findByOrderId(String orderId);
}
