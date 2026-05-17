package com.loopers.domain.user;

public class FakePasswordEncryptor implements PasswordEncryptor {
    private final String encryptPrefix;

    public FakePasswordEncryptor(String encryptPrefix) {
        this.encryptPrefix = encryptPrefix;
    }

    @Override
    public String encrypt(String rawPassword) {
        return encryptPrefix + rawPassword;
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return encrypt(rawPassword).equals(encodedPassword);
    }
}
