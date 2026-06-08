package com.loopers.domain.user;

@FunctionalInterface
public interface PasswordRule {
    void validate(PasswordValidationContext context);
}
