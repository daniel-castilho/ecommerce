package com.loja.useraccount.domain.port.out;

import com.loja.useraccount.application.dto.PageResult;
import com.loja.useraccount.domain.model.AuditLogEvent;

public interface AuditLogQueryPort {
    PageResult<AuditLogEvent> findAuditLogs(int page, int pageSize);
}
