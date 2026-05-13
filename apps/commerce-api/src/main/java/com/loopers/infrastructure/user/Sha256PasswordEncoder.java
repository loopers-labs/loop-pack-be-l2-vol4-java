package com.loopers.infrastructure.user;

import com.loopers.domain.user.EncodedPassword;
import com.loopers.domain.user.PasswordEncoder;
import com.loopers.domain.user.RawPassword;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Component
public class Sha256PasswordEncoder implements PasswordEncoder {

    private static final String ALGORITHM = "SHA-256";

    @Override
    public EncodedPassword encode(RawPassword rawPassword) {
        return new EncodedPassword(hash(rawPassword.value()));
    }

    @Override
    public boolean matches(String rawValue, EncodedPassword encoded) {
        if (rawValue == null || rawValue.isBlank() || encoded == null) {
            return false;
        }
        return hash(rawValue).equals(encoded.value());
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
