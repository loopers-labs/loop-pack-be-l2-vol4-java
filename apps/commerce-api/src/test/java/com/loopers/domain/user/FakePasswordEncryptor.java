package com.loopers.domain.user;

public class FakePasswordEncryptor implements PasswordEncryptor {
    private final String prefix;

    public FakePasswordEncryptor(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String encrypt(String rawPassword) {
        return prefix + rawPassword;
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return encrypt(rawPassword).equals(encodedPassword);
    }
}
