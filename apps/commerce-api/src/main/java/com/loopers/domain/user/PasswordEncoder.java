package com.loopers.domain.user;

public interface PasswordEncoder {
    EncodedPassword encode(RawPassword rawPassword);
}
