package com.loopers.domain.user;

@FunctionalInterface
public interface PasswordEncryptor {
    String encrypt(String rawPassword);
}
