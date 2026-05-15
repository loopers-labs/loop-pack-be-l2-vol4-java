package com.loopers.domain.user;

public interface PasswordHasher {
    String encode(String rawPassword);
    boolean matches(String rawPassword, String passwordHash);
}
