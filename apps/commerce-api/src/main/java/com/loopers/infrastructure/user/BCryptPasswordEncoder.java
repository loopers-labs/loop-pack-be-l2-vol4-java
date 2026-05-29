package com.loopers.infrastructure.user;

import com.loopers.domain.user.EncodedPassword;
import com.loopers.domain.user.PasswordEncoder;
import com.loopers.domain.user.RawPassword;
import org.springframework.stereotype.Component;

/**
 * BCrypt 기반 PasswordEncoder 구현.
 * Spring Security 의 BCryptPasswordEncoder 에 위임한다 - 자동 salt + 적응형 cost.
 * 같은 비밀번호를 두 번 encode 해도 결과 해시는 서로 다르고, matches 로 검증한다.
 */
@Component
public class BCryptPasswordEncoder implements PasswordEncoder {

    private final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder delegate =
        new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();

    @Override
    public EncodedPassword encode(RawPassword rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("rawPassword must not be null");
        }
        return new EncodedPassword(delegate.encode(rawPassword.value()));
    }

    @Override
    public boolean matches(String rawValue, EncodedPassword encoded) {
        if (rawValue == null || rawValue.isBlank() || encoded == null) {
            return false;
        }
        return delegate.matches(rawValue, encoded.value());
    }
}
