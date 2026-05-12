package com.loopers.domain.user;

public interface PasswordEncoder {
    String encode(String rawPassword);
}
