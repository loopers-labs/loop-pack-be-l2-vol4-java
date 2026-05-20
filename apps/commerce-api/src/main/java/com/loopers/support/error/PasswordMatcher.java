package com.loopers.support.error;

@FunctionalInterface
public interface PasswordMatcher {
    boolean matches(String raw, String encoded);
}
