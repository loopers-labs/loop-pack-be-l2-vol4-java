package com.loopers.interfaces.auth;

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

@ExtendWith(MockitoExtension.class)
class AdminAuthInterceptorTest {

    private static final String LDAP_HEADER = "X-Loopers-Ldap";

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Object handler;

    @InjectMocks
    private AdminAuthInterceptor interceptor;

    @DisplayName("preHandle")
    @Nested
    class PreHandle {

        @DisplayName("[ECP] X-Loopers-Ldap 헤더가 없으면 FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenLdapHeaderMissing() {
            // arrange
            given(request.getHeader(LDAP_HEADER)).willReturn(null);

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> interceptor.preHandle(request, response, handler));
            assertEquals(ErrorType.FORBIDDEN, exception.getErrorType());
        }

        @DisplayName("[ECP] X-Loopers-Ldap 헤더 값이 'loopers.admin'이 아니면 FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenLdapValueIsInvalid() {
            // arrange
            given(request.getHeader(LDAP_HEADER)).willReturn("invalid.value");

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> interceptor.preHandle(request, response, handler));
            assertEquals(ErrorType.FORBIDDEN, exception.getErrorType());
        }

        @DisplayName("[ECP] X-Loopers-Ldap 헤더 값이 'loopers.admin'이면 true를 반환한다.")
        @Test
        void returnsTrue_whenLdapValueIsValid() {
            // arrange
            given(request.getHeader(LDAP_HEADER)).willReturn("loopers.admin");

            // act
            boolean result = interceptor.preHandle(request, response, handler);

            // assert
            assertTrue(result);
        }
    }
}
