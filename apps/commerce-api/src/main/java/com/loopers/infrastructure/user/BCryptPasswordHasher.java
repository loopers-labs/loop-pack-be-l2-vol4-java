package com.loopers.infrastructure.user;

import com.loopers.domain.user.PasswordHasher;
import com.loopers.domain.user.vo.EncodedPassword;
import com.loopers.domain.user.vo.PlainPassword;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class BCryptPasswordHasher implements PasswordHasher {

    private final PasswordEncoder passwordEncoder;

    @Override
    public EncodedPassword hash(PlainPassword raw) {
        return EncodedPassword.of(passwordEncoder.encode(raw.value()));
    }

    @Override
    public boolean matches(PlainPassword raw, EncodedPassword encoded) {
        return passwordEncoder.matches(raw.value(), encoded.value());
    }
}