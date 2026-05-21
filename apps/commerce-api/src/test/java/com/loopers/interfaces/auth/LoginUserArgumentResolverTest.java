package com.loopers.interfaces.auth;

import com.loopers.domain.user.BirthDate;
import com.loopers.domain.user.Email;
import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.Password;
import com.loopers.domain.user.UserAuthService;
import com.loopers.domain.user.UserModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LoginUserArgumentResolverTest {

    @Mock
    private UserAuthService userAuthService;

    private LoginUserArgumentResolver resolver;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        resolver = new LoginUserArgumentResolver(userAuthService);
        request = new MockHttpServletRequest();
    }

    @DisplayName("resolveArgument 호출 시,")
    @Nested
    class ResolveArgument {

        @DisplayName("정상 헤더가 주어지면, LoginUser 를 반환한다.")
        @Test
        void returnsLoginUser_whenHeadersAreValid() {
            // given
            request.addHeader(AuthHeaders.LOGIN_ID, "user01");
            request.addHeader(AuthHeaders.LOGIN_PW, "Abcd1234!");
            UserModel user = UserModel.create(
                LoginId.of("user01"),
                Password.encoded("$2a$10$encodedHash"),
                "김철수",
                BirthDate.of(LocalDate.of(1999, 3, 22)),
                Email.of("user@example.com")
            );
            given(userAuthService.authenticate(any(LoginId.class), anyString())).willReturn(user);

            // when
            Object result = resolver.resolveArgument(null, null, new ServletWebRequest(request), null);

            // then
            assertThat(result).isInstanceOf(LoginUser.class);
            LoginUser loginUser = (LoginUser) result;
            assertThat(loginUser.loginId()).isEqualTo("user01");
        }

        @DisplayName("로그인 ID 헤더가 누락되면, UNAUTHORIZED 예외를 던진다.")
        @Test
        void throwsUnauthorized_whenLoginIdHeaderIsMissing() {
            // given
            request.addHeader(AuthHeaders.LOGIN_PW, "Abcd1234!");

            // when
            CoreException exception = assertThrows(CoreException.class, () ->
                resolver.resolveArgument(null, null, new ServletWebRequest(request), null)
            );

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
            verify(userAuthService, never()).authenticate(any(), anyString());
        }

        @DisplayName("비밀번호 헤더가 누락되면, UNAUTHORIZED 예외를 던진다.")
        @Test
        void throwsUnauthorized_whenLoginPwHeaderIsMissing() {
            // given
            request.addHeader(AuthHeaders.LOGIN_ID, "user01");

            // when
            CoreException exception = assertThrows(CoreException.class, () ->
                resolver.resolveArgument(null, null, new ServletWebRequest(request), null)
            );

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
            verify(userAuthService, never()).authenticate(any(), anyString());
        }

        @DisplayName("로그인 ID 형식이 잘못되면, UNAUTHORIZED 예외를 던진다.")
        @Test
        void throwsUnauthorized_whenLoginIdFormatIsInvalid() {
            // given
            request.addHeader(AuthHeaders.LOGIN_ID, "INVALID!!");
            request.addHeader(AuthHeaders.LOGIN_PW, "Abcd1234!");

            // when
            CoreException exception = assertThrows(CoreException.class, () ->
                resolver.resolveArgument(null, null, new ServletWebRequest(request), null)
            );

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
            verify(userAuthService, never()).authenticate(any(), anyString());
        }

        @DisplayName("UserAuthService 가 UNAUTHORIZED 를 던지면, 그대로 전파한다.")
        @Test
        void propagatesUnauthorized_whenAuthServiceFails() {
            // given
            request.addHeader(AuthHeaders.LOGIN_ID, "user01");
            request.addHeader(AuthHeaders.LOGIN_PW, "Wrong9999!");
            given(userAuthService.authenticate(any(LoginId.class), anyString()))
                .willThrow(new CoreException(ErrorType.UNAUTHORIZED));

            // when
            CoreException exception = assertThrows(CoreException.class, () ->
                resolver.resolveArgument(null, null, new ServletWebRequest(request), null)
            );

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }
}
