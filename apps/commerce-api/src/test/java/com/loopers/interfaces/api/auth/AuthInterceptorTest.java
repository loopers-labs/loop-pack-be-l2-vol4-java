package com.loopers.interfaces.api.auth;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthInterceptorTest {

    @InjectMocks
    private AuthInterceptor authInterceptor;

    @Mock
    private UserService userService;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @DisplayName("preHandle()을 호출할 때,")
    @Nested
    class PreHandle {

        @DisplayName("X-Loopers-LoginId 헤더 누락 시 UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenLoginIdHeaderIsMissing() {
            // arrange
            request.addHeader("X-Loopers-LoginPw", "Password1!");

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                authInterceptor.preHandle(request, response, new Object())
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("X-Loopers-LoginPw 헤더 누락 시 UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenLoginPwHeaderIsMissing() {
            // arrange
            request.addHeader("X-Loopers-LoginId", "user123");

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                authInterceptor.preHandle(request, response, new Object())
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("유효한 인증 헤더로 인터셉터 처리 시 currentUser attribute가 저장되고 true가 반환된다.")
        @Test
        void storesCurrentUserAndReturnsTrue_whenCredentialsAreValid() {
            // arrange
            UserModel user = new UserModel("user123", "encoded!", "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
            request.addHeader("X-Loopers-LoginId", "user123");
            request.addHeader("X-Loopers-LoginPw", "Password1!");
            given(userService.authenticate("user123", "Password1!")).willReturn(user);

            // act
            boolean result = authInterceptor.preHandle(request, response, new Object());

            // assert
            assertTrue(result);
            assertThat(request.getAttribute("currentUser")).isEqualTo(user);
        }

        @DisplayName("틀린 인증 정보로 인터셉터 처리 시 UNAUTHORIZED 예외가 전파된다.")
        @Test
        void propagatesUnauthorized_whenAuthenticationFails() {
            // arrange
            request.addHeader("X-Loopers-LoginId", "user123");
            request.addHeader("X-Loopers-LoginPw", "WrongPw!");
            given(userService.authenticate("user123", "WrongPw!"))
                .willThrow(new CoreException(ErrorType.UNAUTHORIZED, "인증에 실패했습니다."));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                authInterceptor.preHandle(request, response, new Object())
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }
}
