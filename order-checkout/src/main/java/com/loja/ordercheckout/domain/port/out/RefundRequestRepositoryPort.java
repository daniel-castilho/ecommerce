package com.loja.ordercheckout.domain.port.out;

import com.loja.ordercheckout.application.dto.PageResult;
import com.loja.ordercheckout.application.dto.RefundSearchCriteria;
import com.loja.ordercheckout.domain.model.RefundRequest;
import java.util.List;
import java.util.Optional;

public interface RefundRequestRepositoryPort {
    void save(RefundRequest request);
    Optional<RefundRequest> findById(String id);

    /** Page of refund requests matching the given criteria (any filter optional). */
    PageResult<RefundRequest> find(RefundSearchCriteria criteria, int page, int pageSize);

    /** All refund requests for an order, newest first (admin order detail). */
    List<RefundRequest> findByOrderId(String orderId);
}
