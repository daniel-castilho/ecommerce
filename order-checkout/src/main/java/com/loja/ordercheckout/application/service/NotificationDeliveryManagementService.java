package com.loja.ordercheckout.application.service;

import com.loja.ordercheckout.domain.model.NotificationDelivery;
import com.loja.ordercheckout.domain.model.NotificationDeliveryStatus;
import com.loja.ordercheckout.domain.port.in.NotificationDeliveryManagementUseCase;
import com.loja.ordercheckout.domain.port.out.NotificationDeliveryLogPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Admin use case over the notification delivery log: read rows (optionally filtered by
 * status) and re-queue a delivery whose attempts were exhausted. Depends only on the
 * delivery-log port; consumed by the admin-dashboard module.
 */
@ApplicationScoped
public class NotificationDeliveryManagementService implements NotificationDeliveryManagementUseCase {

    private final NotificationDeliveryLogPort deliveryLog;

    @Inject
    public NotificationDeliveryManagementService(NotificationDeliveryLogPort deliveryLog) {
        this.deliveryLog = deliveryLog;
    }

    @Override
    public List<NotificationDelivery> listDeliveries(NotificationDeliveryStatus status) {
        return deliveryLog.findDeliveries(status);
    }

    @Override
    public boolean resend(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return false;
        }
        return deliveryLog.resend(idempotencyKey);
    }
}
