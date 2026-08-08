package com.loja.useraccount.domain.port.in;

import com.loja.useraccount.application.dto.AuditLogSearchCriteria;
import com.loja.useraccount.application.dto.PageResult;
import com.loja.useraccount.domain.model.AuditLogEvent;
import java.util.List;

public interface ListAuditLogsUseCase {
    PageResult<AuditLogEvent> listAuditLogs(AuditLogSearchCriteria criteria, int page, int pageSize);

    List<String> distinctEventTypes();
}
