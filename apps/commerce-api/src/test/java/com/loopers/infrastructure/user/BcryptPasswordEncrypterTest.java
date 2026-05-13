package com.loopers.infrastructure.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.loopers.domain.user.PasswordEncrypter;

class BcryptPasswordEncrypterTest {

    private final PasswordEncrypter passwordEncrypter = new BcryptPasswordEncrypter();

    @DisplayName("비밀번호를 암호화할 때,")
    @Nested
    class Encrypt {

        @DisplayName("BCrypt 60자 해시 포맷의 결과를 반환한다.")
        @Test
        void returnsBcryptHash_whenEncryptingRawPassword() {
            // arrange
            String rawPassword = "Kyle!2030";

            // act
            String encryptedPassword = passwordEncrypter.encrypt(rawPassword);

            // assert
            assertThat(encryptedPassword)
                .hasSize(60)
                .startsWith("$2");
        }
    }

    @DisplayName("비밀번호 일치를 검증할 때,")
    @Nested
    class Matches {

        @DisplayName("원본 평문과 암호문이 매칭되면 true를 반환한다.")
        @Test
        void returnsTrue_whenRawPasswordMatchesEncryptedPassword() {
            // arrange
            String rawPassword = "Kyle!2030";
            String encryptedPassword = passwordEncrypter.encrypt(rawPassword);

            // act
            boolean matchingResult = passwordEncrypter.matches(rawPassword, encryptedPassword);

            // assert
            assertThat(matchingResult).isTrue();
        }

        @DisplayName("원본과 다른 평문은 암호문과 매칭되지 않는다.")
        @Test
        void returnsFalse_whenRawPasswordDoesNotMatchEncryptedPassword() {
            // arrange
            String rawPassword = "Kyle!2030";
            String encryptedPassword = passwordEncrypter.encrypt(rawPassword);

            // act
            boolean matchingResult = passwordEncrypter.matches("WrongPassword!1", encryptedPassword);

            // assert
            assertThat(matchingResult).isFalse();
        }
    }
}
