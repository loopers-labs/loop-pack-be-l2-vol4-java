package com.loopers.domain.user;

import com.loopers.domain.value.BirthVO;
import com.loopers.domain.value.EmailVO;
import com.loopers.support.error.PasswordMatcher;
import fixture.UserModelFixture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.time.Month;
import java.util.Objects;
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
            EmailVO emailVO = new EmailVO(email);

            // when
            UserModel userModel = UserModel.of(loginId, name, password, birth, emailVO);

            // then
            Assertions.assertEquals(loginId, userModel.getLoginId());
        }

        @DisplayName("생년월일이 비밀번호에 포함되어 있는 경우 실패한다")
        @Test
        public void userFailTest() {
            // given
            String loginId = "tester";
            String name = "tester";
            BirthVO birth = new BirthVO(LocalDate.of(1993, Month.MARCH, 16));
            String password = "19930316_pwd";
            EmailVO emailVO = new EmailVO("test@test.com");

            // when then
            assertThatThrownBy(() -> UserModel.of(loginId, name, password, birth, emailVO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("비밀번호 생성 규칙 위반 : 생년월일은 비밀번호 내에 포함할 수 없습니다.");
        }

        private static Stream<Arguments> signupSuccessTestParams() {
            return Stream.of(
                Arguments.of("test_1234", "test@test.com"),
                Arguments.of("test_1234_AB", "test@test.com"),
                Arguments.of("test_19940316", "test@test.com")
            );
        }
    }

    @DisplayName("유저 비밀번호 변경 유효성 테스트")
    @MethodSource("validPasswordChangeTestParams")
    @ParameterizedTest(name = "{0}")
    public void validPasswordChangeTest(String description, String originalPassword, String targetPassword, String exceptionWord) {
        // given
        UserModel userModel = UserModelFixture.defaults().toModel();
        PasswordMatcher passwordMatcher = Objects::equals;

        // when then
        assertThatThrownBy(() -> userModel.validPasswordChange(originalPassword, targetPassword, passwordMatcher))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(exceptionWord);
    }

    private static Stream<Arguments> validPasswordChangeTestParams() {
        String originalPassword = UserModelFixture.defaults().password();

        return Stream.of(
            Arguments.of("원본 비밀번호가 같지 않으면 실패한다.", originalPassword + "!!", originalPassword + "__", "비밀번호가 일치하지 않습니다."),
            Arguments.of("바꾸려는 비밀번호가 원본과 같으면 실패한다.", originalPassword, originalPassword, "현재 비밀번호는 사용할 수 없습니다.")
        );
    }
}
