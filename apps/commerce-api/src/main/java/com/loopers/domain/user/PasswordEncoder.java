package com.loopers.domain.user;

public interface PasswordEncoder {
    EncodedPassword encode(RawPassword rawPassword);

    boolean matches(String rawValue, EncodedPassword encoded);
}
