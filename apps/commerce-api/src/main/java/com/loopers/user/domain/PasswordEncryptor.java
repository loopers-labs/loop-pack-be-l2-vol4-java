package com.loopers.user.domain;

public interface PasswordEncryptor {
    String encrypt(String rawPassword);
    boolean matches(String rawPassword, String encodedPassword);
}
