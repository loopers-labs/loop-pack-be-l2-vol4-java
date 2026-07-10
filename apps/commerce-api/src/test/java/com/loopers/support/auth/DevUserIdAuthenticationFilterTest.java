package com.loopers.support.auth;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class DevUserIdAuthenticationFilterTest {

    private final DevUserIdAuthenticationFilter sut = new DevUserIdAuthenticationFilter();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("X-USER-ID 헤더가 있으면 그 값을 principal(userId)로 인증한다")
    void givenUserIdHeader_whenFilter_thenAuthenticates() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(DevUserIdAuthenticationFilter.HEADER, "42");

        sut.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(42L);
    }

    @Test
    @DisplayName("헤더가 없으면 인증을 세팅하지 않고 통과시킨다")
    void givenNoHeader_whenFilter_thenPassesWithoutAuth() throws ServletException, IOException {
        sut.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("숫자가 아닌 값이면 인증을 세팅하지 않는다")
    void givenInvalidHeader_whenFilter_thenPassesWithoutAuth() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(DevUserIdAuthenticationFilter.HEADER, "abc");

        sut.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
