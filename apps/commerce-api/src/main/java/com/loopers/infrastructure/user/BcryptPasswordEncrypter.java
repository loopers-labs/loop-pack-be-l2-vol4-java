package com.loopers.infrastructure.user;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.loopers.domain.user.PasswordEncrypter;

@Component
public class BcryptPasswordEncrypter implements PasswordEncrypter {

    private final BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();

    @Override
    public String encrypt(String rawPassword) {
        return bCryptPasswordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encryptedPassword) {
        return bCryptPasswordEncoder.matches(rawPassword, encryptedPassword);
    }
}
