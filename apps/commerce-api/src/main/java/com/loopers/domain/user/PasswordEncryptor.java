package com.loopers.domain.user;

public interface PasswordEncryptor {
    String encrypt(String rawPassword);
    boolean matches(String rawPassword, String encodedPassword);
}
