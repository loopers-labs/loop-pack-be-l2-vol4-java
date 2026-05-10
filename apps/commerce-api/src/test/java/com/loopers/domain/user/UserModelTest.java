package com.loopers.domain.user;

import com.loopers.domain.value.BirthVO;
import com.loopers.domain.value.EmailVO;
import com.loopers.domain.value.PasswordVO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.time.Month;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UserModelTest {

    @DisplayName("유저 모델을 생성할 때")
    @Nested
    class Create {
        
        @DisplayName("유효한 유저의 경우에는 성공한다.")
        @MethodSource("signupSuccessTestParams")
        @ParameterizedTest
        public void userSuccessTest(String password, String email) {
            // given
            String loginId = "tester";
            String name = "tester";
            BirthVO birth = new BirthVO(LocalDate.of(1993, Month.MARCH, 16));
            PasswordVO passwordVO = new PasswordVO(password);
            EmailVO emailVO = new EmailVO(email);

            // when
            UserModel userModel = UserModel.of(loginId, name, birth, passwordVO, emailVO);

            // then
            Assertions.assertEquals(loginId, userModel.getLoginId());
        }

        private static Stream<Arguments> signupSuccessTestParams() {
            return Stream.of(
                    Arguments.of("test_1234", "test@test.com"),
                    Arguments.of("test_1234_AB", "test@test.com"),
                    Arguments.of("test_19940316", "test@test.com")
            );
        }

        @DisplayName("생년월일이 비밀번호에 포함되어 있는 경우 실패한다")
        @Test
        public void userFailTest() {
            // given
            String loginId = "tester";
            String name = "tester";
            BirthVO birth = new BirthVO(LocalDate.of(1993, Month.MARCH, 16));
            PasswordVO passwordVO = new PasswordVO("19930316_pwd");
            EmailVO emailVO = new EmailVO("test@test.com");

            // when then
            assertThatThrownBy(() -> UserModel.of(loginId, name, birth, passwordVO, emailVO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("비밀번호 생성 규칙 위반 : 생년월일은 비밀번호 내에 포함할 수 없습니다.");
        }
    }
}
