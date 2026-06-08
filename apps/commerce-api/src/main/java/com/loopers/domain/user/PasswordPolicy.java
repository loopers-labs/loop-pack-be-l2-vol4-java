package com.loopers.domain.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class PasswordPolicy {

    private final List<PasswordRule> rules;

    public void validate(PasswordValidationContext context) {
        rules.forEach(rule -> rule.validate(context));
    }
}
