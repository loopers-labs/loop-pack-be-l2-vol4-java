package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.loopers.infrastructure.user.BcryptPasswordEncrypter;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class EncryptedPasswordTest {

    private final PasswordEncrypter passwordEncrypter = new BcryptPasswordEncrypter();

    @DisplayName("EncryptedPassword를 암호화 생성할 때,")
    @Nested
    class Encrypt {

        @DisplayName("정책을 통과하는 평문이면 평문과 다른 암호문을 보관한 EncryptedPassword가 생성된다.")
        @ParameterizedTest
        @ValueSource(strings = {"abcd1234", "Kyle!2030", "P@ssw0rd!2030"})
        void createsEncryptedPassword_whenRawPasswordPassesPolicy(String rawPassword) {
            // act
            EncryptedPassword encryptedPassword = EncryptedPassword.encrypt(rawPassword, passwordEncrypter);

            // assert
            assertThat(encryptedPassword.value())
                .isNotBlank()
                .isNotEqualTo(rawPassword);
        }

        @DisplayName("길이가 8자 미만이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"a1!", "abc123!"})
        void throwsBadRequest_whenRawPasswordIsShorterThanMinLength(String rawPassword) {
            // act & assert
            assertThatThrownBy(() -> EncryptedPassword.encrypt(rawPassword, passwordEncrypter))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("길이가 16자 초과면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"abcdefg12345678!a", "abcdefghij12345678!"})
        void throwsBadRequest_whenRawPasswordIsLongerThanMaxLength(String rawPassword) {
            // act & assert
            assertThatThrownBy(() -> EncryptedPassword.encrypt(rawPassword, passwordEncrypter))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("허용 외 문자(공백, 한글)가 포함되면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"abc 1234", "Kyle 2030!", "abcd123한", "한글비밀번호123!"})
        void throwsBadRequest_whenRawPasswordContainsForbiddenCharacters(String rawPassword) {
            // act & assert
            assertThatThrownBy(() -> EncryptedPassword.encrypt(rawPassword, passwordEncrypter))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("null이거나 빈 문자열이거나 공백 문자로만 이루어진 문자열이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "      ", "\t", "\n", "\r"})
        void throwsBadRequest_whenRawPasswordIsNullOrBlank(String rawPassword) {
            // act & assert
            assertThatThrownBy(() -> EncryptedPassword.encrypt(rawPassword, passwordEncrypter))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
