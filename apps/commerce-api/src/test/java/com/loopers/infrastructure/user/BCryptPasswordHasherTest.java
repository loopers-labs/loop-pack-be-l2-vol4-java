package com.loopers.infrastructure.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BCryptPasswordHasherTest {

    private BCryptPasswordHasher passwordHasher;

    @BeforeEach
    void setUp() {
        passwordHasher = new BCryptPasswordHasher();
    }

    @DisplayName("비밀번호를 해싱할 때,")
    @Nested
    class Hash {

        @DisplayName("평문 비밀번호를 해싱하면, 원문과 다른 값이 반환된다.")
        @Test
        void returns_differentValue_whenHashed() {
            // arrange
            String rawPassword = "Password1!";

            // act
            String hashed = passwordHasher.hash(rawPassword);

            // assert
            assertThat(hashed).isNotEqualTo(rawPassword);
        }

        @DisplayName("같은 평문을 두 번 해싱하면, 서로 다른 해시값이 반환된다.")
        @Test
        void returns_differentHash_whenHashedTwice() {
            // arrange
            String rawPassword = "Password1!";

            // act
            String hash1 = passwordHasher.hash(rawPassword);
            String hash2 = passwordHasher.hash(rawPassword);

            // assert
            assertThat(hash1).isNotEqualTo(hash2);
        }
    }

    @DisplayName("비밀번호를 검증할 때,")
    @Nested
    class Matches {

        @DisplayName("평문과 해시값이 일치하면, true를 반환한다.")
        @Test
        void returnsTrue_whenMatches() {
            // arrange
            String rawPassword = "Password1!";
            String hashed = passwordHasher.hash(rawPassword);

            // act
            boolean result = passwordHasher.matches(rawPassword, hashed);

            // assert
            assertThat(result).isTrue();
        }

        @DisplayName("평문과 해시값이 일치하지 않으면, false를 반환한다.")
        @Test
        void returnsFalse_whenNotMatches() {
            // arrange
            String rawPassword = "Password1!";
            String hashed = passwordHasher.hash(rawPassword);

            // act
            boolean result = passwordHasher.matches("WrongPassword1!", hashed);

            // assert
            assertThat(result).isFalse();
        }
    }
}
