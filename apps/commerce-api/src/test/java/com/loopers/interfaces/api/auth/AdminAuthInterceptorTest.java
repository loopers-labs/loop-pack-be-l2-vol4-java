package com.loopers.interfaces.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class AdminAuthInterceptorTest {

    private static final String LDAP_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP = "loopers.admin";

    private final AdminAuthInterceptor adminAuthInterceptor = new AdminAuthInterceptor();

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @DisplayName("관리자 요청을 검사할 때,")
    @Nested
    class PreHandle {

        @DisplayName("X-Loopers-Ldap 헤더가 loopers.admin이면 통과한다.")
        @Test
        void returnsTrue_whenLdapHeaderIsAdmin() {
            // arrange
            request.addHeader(LDAP_HEADER, ADMIN_LDAP);

            // act
            boolean passed = adminAuthInterceptor.preHandle(request, response, new Object());

            // assert
            assertThat(passed).isTrue();
        }

        @DisplayName("X-Loopers-Ldap 헤더가 없으면 FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenLdapHeaderIsMissing() {
            // arrange & act & assert
            assertThatThrownBy(() -> adminAuthInterceptor.preHandle(request, response, new Object()))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.FORBIDDEN);
        }

        @DisplayName("X-Loopers-Ldap 헤더 값이 올바르지 않으면 FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenLdapHeaderIsInvalid() {
            // arrange
            request.addHeader(LDAP_HEADER, "someone.else");

            // act & assert
            assertThatThrownBy(() -> adminAuthInterceptor.preHandle(request, response, new Object()))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.FORBIDDEN);
        }
    }
}
