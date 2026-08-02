package com.loja.useraccount.domain.port.out;

/** Output port for password hashing (Argon2id). */
public interface PasswordHasherPort {
    /** Hashes a plaintext password. */
    String hash(String plainPassword);

    /** Verifies a plaintext password against a stored hash. */
    boolean verify(String plainPassword, String hash);
}
