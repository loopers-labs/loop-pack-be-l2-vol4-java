package com.loopers.domain.user;

public interface PasswordHasher {
    String encode(String raw);
    boolean matches(String raw, String encoded);
}
