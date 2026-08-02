package com.loja.useraccount.adapter.auth;

import com.loja.useraccount.domain.port.out.PasswordHasherPort;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import jakarta.enterprise.context.ApplicationScoped;

/** Password hashing adapter using Argon2id (resistant to GPU/ASIC brute-force attacks). */
@ApplicationScoped
public class PasswordHasherArgon2Adapter implements PasswordHasherPort {

    private static final Argon2 ARGON2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);

    @Override
    public String hash(String plainPassword) {
        if (plainPassword == null || plainPassword.isBlank()) {
            throw new IllegalArgumentException("Password cannot be blank");
        }
        return ARGON2.hash(2, 65536, 1, plainPassword.toCharArray());
    }

    @Override
    public boolean verify(String plainPassword, String hash) {
        if (plainPassword == null || hash == null) {
            return false;
        }
        return ARGON2.verify(hash, plainPassword.toCharArray());
    }
}
