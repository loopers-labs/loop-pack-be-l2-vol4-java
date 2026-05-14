package com.loopers.infrastructure.user;

import com.loopers.domain.user.PasswordEncoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BCryptPasswordEncoderAdapterTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoderAdapter();

    @DisplayName("matches 호출 시,")
    @Nested
    class Matches {

        @DisplayName("원본과 동일한 비밀번호로 인코딩된 값이면, true 를 반환한다.")
        @Test
        void returnsTrue_whenRawMatchesEncoded() {
            // given
            String raw = "Abcd1234!";
            String encoded = passwordEncoder.encode(raw);

            // when
            boolean result = passwordEncoder.matches(raw, encoded);

            // then
            assertThat(result).isTrue();
        }

        @DisplayName("원본과 다른 비밀번호로 인코딩된 값이면, false 를 반환한다.")
        @Test
        void returnsFalse_whenRawDoesNotMatchEncoded() {
            // given
            String raw = "Abcd1234!";
            String encoded = passwordEncoder.encode("Different9!");

            // when
            boolean result = passwordEncoder.matches(raw, encoded);

            // then
            assertThat(result).isFalse();
        }
    }
}
