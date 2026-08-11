package com.loja.ordercheckout.domain.port.in;

import com.loja.ordercheckout.domain.model.NotificationDelivery;
import com.loja.ordercheckout.domain.model.NotificationDeliveryStatus;
import java.util.List;

/** Input port (admin-only): inspect the notification delivery log and re-queue deliveries. */
public interface NotificationDeliveryManagementUseCase {

    /**
     * Lists delivery rows, newest first. Pass a {@code null} status for all rows.
     */
    List<NotificationDelivery> listDeliveries(NotificationDeliveryStatus status);

    /**
     * Manually re-queues a delivery (e.g. one that reached EXHAUSTED) so the poller
     * dispatches it again.
     *
     * @return {@code true} if the delivery was re-queued, {@code false} if the key is unknown
     */
    boolean resend(String idempotencyKey);
}
