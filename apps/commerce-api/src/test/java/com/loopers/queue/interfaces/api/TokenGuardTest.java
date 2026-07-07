package com.loopers.queue.interfaces.api;

import com.loopers.queue.domain.EntryTokenStore;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TokenGuardTest {

    private static final Long USER_ID = 42L;

    private final EntryTokenStore entryTokenStore = mock(EntryTokenStore.class);
    private final TokenGuard sut = new TokenGuard(entryTokenStore);

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID, null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("토큰 헤더가 없으면 예외를 던진다")
    void givenNoTokenHeader_whenPreHandle_thenThrows() {
        assertThatThrownBy(() -> sut.preHandle(post(null), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(CoreException.class);
    }

    @Test
    @DisplayName("토큰이 저장된 값과 다르면 예외를 던진다")
    void givenWrongToken_whenPreHandle_thenThrows() {
        when(entryTokenStore.find("42")).thenReturn(Optional.of("real-token"));

        assertThatThrownBy(() -> sut.preHandle(post("fake-token"), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(CoreException.class);
    }

    @Test
    @DisplayName("올바른 토큰이면 통과한다")
    void givenValidToken_whenPreHandle_thenPasses() {
        when(entryTokenStore.find("42")).thenReturn(Optional.of("real-token"));

        boolean result = sut.preHandle(post("real-token"), new MockHttpServletResponse(), new Object());

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("GET 요청은 토큰 검증을 건너뛴다")
    void givenGetRequest_whenPreHandle_thenPasses() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");

        boolean result = sut.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(result).isTrue();
    }

    private MockHttpServletRequest post(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        if (token != null) {
            request.addHeader(TokenGuard.HEADER, token);
        }
        return request;
    }
}
