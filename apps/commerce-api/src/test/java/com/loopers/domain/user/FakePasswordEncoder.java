package com.loopers.domain.user;

import java.util.Optional;

public class FakePasswordEncoder implements PasswordEncoder {

    private static final String PREFIX = "encoded:";

    @Override
    public String encode(String raw) {
        return PREFIX+raw;
    }

    @Override
    public boolean matches(String raw, String encoded) {
        return Optional.ofNullable(encoded)
                .map(value -> value.equals(PREFIX + raw))
                .orElse(false);
    }
}
