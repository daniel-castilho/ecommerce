package com.loja.ordercheckout.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GuestCartSessionTest {

    @Test
    void getGuestId_generatedOnceAndStableAcrossCalls() {
        GuestCartSession session = new GuestCartSession();

        String first = session.getGuestId();
        String second = session.getGuestId();

        assertThat(first).isNotBlank();
        assertThat(second).isEqualTo(first);
    }

    @Test
    void getGuestId_afterReset_generatesFreshId() {
        GuestCartSession session = new GuestCartSession();
        String original = session.getGuestId();

        session.reset();

        assertThat(session.getGuestId()).isNotEqualTo(original);
    }

    @Test
    void reset_whenNeverUsed_isNoOp() {
        GuestCartSession session = new GuestCartSession();

        session.reset();

        assertThat(session.getGuestId()).isNotBlank();
    }
}
