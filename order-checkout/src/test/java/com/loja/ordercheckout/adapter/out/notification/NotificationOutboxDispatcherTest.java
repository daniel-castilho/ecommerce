package com.loja.ordercheckout.adapter.out.notification;

import jakarta.enterprise.concurrent.ManagedScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationOutboxDispatcherTest {

    @Test
    void stopPolling_cancelsScheduledTask() {
        ManagedScheduledExecutorService scheduler = mock(ManagedScheduledExecutorService.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> task = mock(ScheduledFuture.class);
        doReturn(task).when(scheduler).scheduleWithFixedDelay(
                org.mockito.ArgumentMatchers.any(Runnable.class),
                org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.eq(TimeUnit.SECONDS));
        NotificationOutboxDispatcher dispatcher =
                new NotificationOutboxDispatcher(scheduler, mock(NotificationOutboxProcessor.class));

        dispatcher.schedulePolling(new Object());
        dispatcher.stopPolling(new Object());

        verify(task).cancel(false);
    }

    @Test
    void stopPolling_noTaskScheduled_doesNothing() {
        NotificationOutboxDispatcher dispatcher = new NotificationOutboxDispatcher(
                mock(ManagedScheduledExecutorService.class), mock(NotificationOutboxProcessor.class));

        dispatcher.stopPolling(new Object());
    }
}
