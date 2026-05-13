package com.loopers.interfaces.api.user;

import com.loopers.domain.user.Birth;
import com.loopers.domain.user.Email;
import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.Name;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class LoginUserArgumentResolverTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private LoginUserArgumentResolver resolver;

    @DisplayName("supportsParameter 는, ")
    @Nested
    class SupportsParameter {

        @DisplayName("@LoginUser 어노테이션이 없는 파라미터에 대해 false 를 반환한다.")
        @Test
        void returnsFalse_whenParameterDoesNotHaveLoginUserAnnotation() throws Exception {
            // arrange
            Method method = TargetController.class.getDeclaredMethod("withoutAnnotation", User.class);
            MethodParameter parameter = new MethodParameter(method, 0);

            // act
            boolean result = resolver.supportsParameter(parameter);

            // assert
            assertThat(result).isFalse();
        }
    }

    static class TargetController {
        public void withoutAnnotation(User user) {}
    }

    @DisplayName("resolveArgument 는, ")
    @Nested
    class ResolveArgument {

        @DisplayName("X-Loopers-LoginId 와 X-Loopers-LoginPw 헤더가 유효하면, 인증된 User 를 반환한다.")
        @Test
        void returnsAuthenticatedUser_whenHeadersAreValid() {
            // arrange
            String loginIdHeader = "minwoo01";
            String password = "Passw0rd!";
            User authenticatedUser = new User(
                new LoginId(loginIdHeader),
                new Name("김민우"),
                new Birth(LocalDate.of(1990, 1, 1)),
                new Email("minwoo@example.com"),
                "ENCODED"
            );
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Loopers-LoginId", loginIdHeader);
            request.addHeader("X-Loopers-LoginPw", password);
            ServletWebRequest webRequest = new ServletWebRequest(request);
            given(userService.authenticate(new LoginId(loginIdHeader), password))
                .willReturn(authenticatedUser);

            // act
            Object result = resolver.resolveArgument(null, null, webRequest, null);

            // assert
            assertThat(result).isSameAs(authenticatedUser);
        }

        @DisplayName("X-Loopers-LoginId 헤더가 없으면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenLoginIdHeaderIsMissing() {
            // arrange
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Loopers-LoginPw", "Passw0rd!");
            ServletWebRequest webRequest = new ServletWebRequest(request);

            // act
            CoreException result = assertThrows(
                CoreException.class,
                () -> resolver.resolveArgument(null, null, webRequest, null)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("X-Loopers-LoginPw 헤더가 없으면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenLoginPwHeaderIsMissing() {
            // arrange
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Loopers-LoginId", "minwoo01");
            ServletWebRequest webRequest = new ServletWebRequest(request);

            // act
            CoreException result = assertThrows(
                CoreException.class,
                () -> resolver.resolveArgument(null, null, webRequest, null)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("X-Loopers-LoginId 헤더 값이 빈 문자열이거나 공백만 있으면, UNAUTHORIZED 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void throwsUnauthorized_whenLoginIdHeaderIsBlank(String loginIdHeader) {
            // arrange
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Loopers-LoginId", loginIdHeader);
            request.addHeader("X-Loopers-LoginPw", "Passw0rd!");
            ServletWebRequest webRequest = new ServletWebRequest(request);

            // act
            CoreException result = assertThrows(
                CoreException.class,
                () -> resolver.resolveArgument(null, null, webRequest, null)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("X-Loopers-LoginPw 헤더 값이 빈 문자열이거나 공백만 있으면, UNAUTHORIZED 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void throwsUnauthorized_whenLoginPwHeaderIsBlank(String password) {
            // arrange
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Loopers-LoginId", "minwoo01");
            request.addHeader("X-Loopers-LoginPw", password);
            ServletWebRequest webRequest = new ServletWebRequest(request);

            // act
            CoreException result = assertThrows(
                CoreException.class,
                () -> resolver.resolveArgument(null, null, webRequest, null)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }
}
