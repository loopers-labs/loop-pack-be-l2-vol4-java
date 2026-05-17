package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LoginIdTest {

    @DisplayName("로그인 아이디를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("정상적으로 생성된다.")
        @Test
        void createLoginId() {
            // given
            String value = "loginId";

            // when
            LoginId loginId = new LoginId(value);

            // then
            assertThat(loginId).isEqualTo(new LoginId(value));
        }

        @DisplayName("영문/숫자 10자 이내 형식에 맞지 않으면 BAD_REQUEST 예외가 발생한다")
        @NullAndEmptySource
        @ValueSource(strings = {"useruseruser", "홍길동user", "user!01", "user 01"})
        @ParameterizedTest
        void throwsBadRequestException_whenLoginIdIsInvalid(String loginId) {
            // when
            CoreException result = assertThrows(CoreException.class, () -> new LoginId(loginId));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

}
