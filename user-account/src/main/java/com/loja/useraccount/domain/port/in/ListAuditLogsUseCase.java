package com.loja.useraccount.domain.port.in;

import com.loja.useraccount.application.dto.PageResult;
import com.loja.useraccount.domain.model.AuditLogEvent;

public interface ListAuditLogsUseCase {
    PageResult<AuditLogEvent> listAuditLogs(int page, int pageSize);
}
