package com.loopers.infrastructure.user;

import com.loopers.domain.user.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String encode(String raw) {
        return encoder.encode(raw);
    }

    @Override
    public boolean matches(String raw, String encoded) {
        return encoder.matches(raw, encoded);
    }
}
