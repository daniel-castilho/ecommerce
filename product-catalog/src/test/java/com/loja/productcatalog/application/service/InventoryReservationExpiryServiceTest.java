package com.loja.productcatalog.application.service;

import com.loja.productcatalog.domain.port.out.InventoryReservationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryReservationExpiryServiceTest {

    private final InventoryReservationPort inventoryReservation = mock(InventoryReservationPort.class);

    private InventoryReservationExpiryService service;

    @BeforeEach
    void setUp() {
        service = new InventoryReservationExpiryService();
        service.inventoryReservation = inventoryReservation;
    }

    @Test
    void releaseExpired_delegatesToPortAndReturnsReleasedCount() {
        when(inventoryReservation.expireExpired()).thenReturn(4);

        int released = service.releaseExpired();

        assertThat(released).isEqualTo(4);
        verify(inventoryReservation).expireExpired();
    }

    @Test
    void releaseExpired_nothingExpired_returnsZero() {
        when(inventoryReservation.expireExpired()).thenReturn(0);

        int released = service.releaseExpired();

        assertThat(released).isZero();
        verify(inventoryReservation).expireExpired();
    }
}
