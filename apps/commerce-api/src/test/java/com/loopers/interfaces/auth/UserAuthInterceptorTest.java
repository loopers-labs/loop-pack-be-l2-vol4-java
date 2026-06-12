package com.loopers.interfaces.auth;

import com.loopers.application.user.UserApplicationService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserAuthInterceptorTest {

    private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    private static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";

    @Mock
    private UserApplicationService userApplicationService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Object handler;

    @InjectMocks
    private UserAuthInterceptor interceptor;

    @DisplayName("preHandle")
    @Nested
    class PreHandle {

        @DisplayName("[ECP] X-Loopers-LoginId 헤더가 없으면 UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenLoginIdHeaderMissing() {
            // arrange
            given(request.getHeader(LOGIN_ID_HEADER)).willReturn(null);

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> interceptor.preHandle(request, response, handler));
            assertEquals(ErrorType.UNAUTHORIZED, exception.getErrorType());
        }

        @DisplayName("[ECP] X-Loopers-LoginPw 헤더가 없으면 UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenLoginPwHeaderMissing() {
            // arrange
            given(request.getHeader(LOGIN_ID_HEADER)).willReturn("testuser");
            given(request.getHeader(LOGIN_PW_HEADER)).willReturn(null);

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> interceptor.preHandle(request, response, handler));
            assertEquals(ErrorType.UNAUTHORIZED, exception.getErrorType());
        }

        @DisplayName("[ECP] 유효한 헤더로 인증 성공 시 request attribute에 userId를 저장하고 true를 반환한다.")
        @Test
        void storesUserIdAndReturnsTrue_whenAuthSuccess() {
            // arrange
            given(request.getHeader(LOGIN_ID_HEADER)).willReturn("testuser");
            given(request.getHeader(LOGIN_PW_HEADER)).willReturn("Password1!");
            given(userApplicationService.authenticate("testuser", "Password1!")).willReturn(1L);

            // act
            boolean result = interceptor.preHandle(request, response, handler);

            // assert
            assertTrue(result);
            verify(request).setAttribute("userId", 1L);
        }

        @DisplayName("[ECP] 인증 실패 시 UserApplicationService에서 발생한 UNAUTHORIZED 예외가 전파된다.")
        @Test
        void propagatesException_whenAuthFails() {
            // arrange
            given(request.getHeader(LOGIN_ID_HEADER)).willReturn("testuser");
            given(request.getHeader(LOGIN_PW_HEADER)).willReturn("wrongpw");
            given(userApplicationService.authenticate("testuser", "wrongpw"))
                    .willThrow(new CoreException(ErrorType.UNAUTHORIZED, "인증 실패"));

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> interceptor.preHandle(request, response, handler));
            assertEquals(ErrorType.UNAUTHORIZED, exception.getErrorType());
        }
    }
}
