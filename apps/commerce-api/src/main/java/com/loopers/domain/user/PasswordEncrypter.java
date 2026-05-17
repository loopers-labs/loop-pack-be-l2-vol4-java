package com.loopers.domain.user;

public interface PasswordEncrypter {

    String encrypt(String rawPassword);

    boolean matches(String rawPassword, String encryptedPassword);
}
