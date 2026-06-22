package com.loopers.domain.user;

public interface PasswordHasher {

    String hash(String raw);

    boolean matches(String raw, String encoded);
}
