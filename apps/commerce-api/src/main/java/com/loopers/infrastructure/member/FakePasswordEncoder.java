package com.loopers.infrastructure.member;

import com.loopers.domain.member.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class FakePasswordEncoder implements PasswordEncoder {

    @Override
    public String encode(String rawPassword) {
        return Base64.getEncoder().encodeToString(rawPassword.getBytes());
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return encode(rawPassword).equals(encodedPassword);
    }
}
