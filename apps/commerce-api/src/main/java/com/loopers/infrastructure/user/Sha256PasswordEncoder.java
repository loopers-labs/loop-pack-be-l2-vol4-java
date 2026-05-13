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
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hash = digest.digest(rawPassword.value().getBytes(StandardCharsets.UTF_8));
            return new EncodedPassword(Base64.getEncoder().encodeToString(hash));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
