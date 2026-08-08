package com.loja.useraccount.domain.port.out;

import com.loja.useraccount.application.dto.AuditLogSearchCriteria;
import com.loja.useraccount.application.dto.PageResult;
import com.loja.useraccount.domain.model.AuditLogEvent;
import java.util.List;

public interface AuditLogQueryPort {
    PageResult<AuditLogEvent> findAuditLogs(AuditLogSearchCriteria criteria, int page, int pageSize);

    List<String> distinctEventTypes();
}
