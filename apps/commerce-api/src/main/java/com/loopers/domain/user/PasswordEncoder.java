package com.loopers.domain.user;

public interface PasswordEncoder {

    String encode(String raw);

    boolean matches(String raw, String encoded);
}
