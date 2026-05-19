package com.loopers.domain.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class PasswordEncryptor {

    private final PasswordEncoder passwordEncoder;

    public String encode(String raw, LocalDate birthDate) {
        RawPassword password = new RawPassword(raw);
        PasswordPolicy.validate(password, birthDate);
        return passwordEncoder.encode(password.value());
    }

    public boolean matches(String raw, String encoded) {
        if (raw == null || raw.isBlank() || encoded == null || encoded.isBlank()) {
            return false;
        }
        return passwordEncoder.matches(raw, encoded);
    }
}
