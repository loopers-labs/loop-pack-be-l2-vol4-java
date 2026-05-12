package com.loopers.interfaces.api;

import com.loopers.domain.user.Gender;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.request.NativeWebRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class LoginUserResolverTest {

    @Mock private UserService userService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private NativeWebRequest webRequest;
    @Mock private MethodParameter methodParameter;

    @InjectMocks private LoginUserResolver resolver;

    @DisplayName("resolveArgument를 호출할 때,")
    @Nested
    class ResolveArgument {

        @DisplayName("헤더가 유효하면, LoginUser가 반환된다.")
        @Test
        void returnsLoginUser_whenHeadersAreValid() {
            // arrange
            String loginId = "user1";
            String loginPw = "Pass123!";
            UserModel user = new UserModel(loginId, loginPw, "홍길동", "test@example.com", "2000-01-01", Gender.MALE);

            given(webRequest.getHeader("X-Loopers-LoginId")).willReturn(loginId);
            given(webRequest.getHeader("X-Loopers-LoginPw")).willReturn(loginPw);
            given(userService.findByLoginId(loginId)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(loginPw, user.getPassword())).willReturn(true);

            // act
            LoginUser result = (LoginUser) resolver.resolveArgument(null, null, webRequest, null);

            // assert
            assertAll(
                () -> assertThat(result).isNotNull(),
                () -> assertThat(result.loginId()).isEqualTo(loginId)
            );
        }

        @DisplayName("X-Loopers-LoginId 헤더가 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdHeaderIsMissing() {
            // arrange
            given(webRequest.getHeader("X-Loopers-LoginId")).willReturn(null);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                resolver.resolveArgument(null, null, webRequest, null)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("X-Loopers-LoginPw 헤더가 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginPwHeaderIsMissing() {
            // arrange
            given(webRequest.getHeader("X-Loopers-LoginId")).willReturn("user1");
            given(webRequest.getHeader("X-Loopers-LoginPw")).willReturn(null);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                resolver.resolveArgument(null, null, webRequest, null)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 loginId이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenUserDoesNotExist() {
            // arrange
            given(webRequest.getHeader("X-Loopers-LoginId")).willReturn("unknown");
            given(webRequest.getHeader("X-Loopers-LoginPw")).willReturn("Pass123!");
            given(userService.findByLoginId("unknown")).willReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                resolver.resolveArgument(null, null, webRequest, null)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("비밀번호가 일치하지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordDoesNotMatch() {
            // arrange
            String loginId = "user1";
            UserModel user = new UserModel(loginId, "Pass123!", "홍길동", "test@example.com", "2000-01-01", Gender.MALE);

            given(webRequest.getHeader("X-Loopers-LoginId")).willReturn(loginId);
            given(webRequest.getHeader("X-Loopers-LoginPw")).willReturn("WrongPw1!");
            given(userService.findByLoginId(loginId)).willReturn(Optional.of(user));
            given(passwordEncoder.matches("WrongPw1!", user.getPassword())).willReturn(false);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                resolver.resolveArgument(null, null, webRequest, null)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
