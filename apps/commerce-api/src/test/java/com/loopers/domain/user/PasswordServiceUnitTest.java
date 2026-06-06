package com.loopers.domain.user;

import com.loopers.domain.user.vo.BirthDay;
import com.loopers.domain.user.vo.Password;
import com.loopers.domain.user.vo.RawPassword;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordServiceUnitTest {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final PasswordService sut = new PasswordService(passwordEncoder);

    private static final String RAW_PASSWORD = "Dlaxodid1!";
    private static final String BIRTHDAY = "1990-01-01";

    @DisplayName("비밀번호 인코딩 시,")
    @Nested
    class Encode {

        @DisplayName("인코딩된 비밀번호가 원래 평문과 매칭된다.")
        @Test
        void returnsEncodedPassword_whenRawPasswordIsGiven() {
            Password result = sut.encode(new RawPassword(RAW_PASSWORD));

            assertThat(passwordEncoder.matches(RAW_PASSWORD, result.getValue())).isTrue();
        }
    }

    @DisplayName("생년월일 포함 여부 검증 시,")
    @Nested
    class ValidateNotContainBirthDay {

        @DisplayName("생년월일이 포함된 비밀번호면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDay() {
            CoreException result = assertThrows(CoreException.class, () ->
                    sut.validateNotContainBirthDay(new RawPassword("19900101Abc!"), new BirthDay(BIRTHDAY))
            );

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일이 포함되지 않은 비밀번호면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenPasswordNotContainsBirthDay() {
            assertThatNoException().isThrownBy(() ->
                    sut.validateNotContainBirthDay(new RawPassword(RAW_PASSWORD), new BirthDay(BIRTHDAY))
            );
        }
    }

    @DisplayName("현재 비밀번호 동일 여부 검증 시,")
    @Nested
    class ValidateNotSamePassword {

        @DisplayName("현재 비밀번호와 동일하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenSamePasswordIsGiven() {
            Password currentPassword = sut.encode(new RawPassword(RAW_PASSWORD));

            CoreException result = assertThrows(CoreException.class, () ->
                    sut.validateNotSamePassword(new RawPassword(RAW_PASSWORD), currentPassword)
            );

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("현재 비밀번호와 다르면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenDifferentPasswordIsGiven() {
            Password currentPassword = sut.encode(new RawPassword(RAW_PASSWORD));

            assertThatNoException().isThrownBy(() ->
                    sut.validateNotSamePassword(new RawPassword("Dlaxodid2!"), currentPassword)
            );
        }
    }
}
