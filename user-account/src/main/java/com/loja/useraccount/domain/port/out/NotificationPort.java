package com.loja.useraccount.domain.port.out;

public interface NotificationPort {
    void sendWelcomeEmail(String email, String fullName);
    void sendPasswordResetEmail(String email, String token);
}
