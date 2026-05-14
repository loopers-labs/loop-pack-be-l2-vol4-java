package com.loopers.domain.user;

public class FakePasswordEncoder implements PasswordEncoder {

    private static final String PREFIX = "encoded:";

    @Override
    public String encode(String raw) {
        return PREFIX+raw;
    }

    @Override
    public boolean matches(String raw, String encoded) {
        return encoded.equals(PREFIX+raw);
    }
}
