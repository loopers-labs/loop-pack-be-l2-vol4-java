package com.loopers.tddstudy.interfaces.api.order;

import com.loopers.tddstudy.application.queue.EntryTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class EntryTokenInterceptorTest {

    private EntryTokenService tokenService;
    private EntryTokenInterceptor interceptor;

    @BeforeEach
    void setUp() {
        tokenService = mock(EntryTokenService.class);
        interceptor = new EntryTokenInterceptor(tokenService);
    }

    @Test
    @DisplayName("유효한 토큰이면 통과한다")
    void pass_with_valid_token() throws Exception {
        when(tokenService.validateAndConsume(1L, "good")).thenReturn(true);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-USER-ID", "1");
        req.addHeader("X-Entry-Token", "good");
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(req, res, new Object())).isTrue();
    }

    @Test
    @DisplayName("토큰이 없거나 무효면 403으로 차단한다")
    void block_without_token() throws Exception {
        when(tokenService.validateAndConsume(anyLong(), any())).thenReturn(false);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-USER-ID", "1");                 // 토큰 헤더 없음
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(req, res, new Object())).isFalse();
        assertThat(res.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("X-USER-ID 헤더가 없으면 401")
    void unauthorized_without_userId() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(req, res, new Object())).isFalse();
        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("Redis 장애 시 503 + Retry-After 로 부드럽게 차단한다")
    void degrade_on_redis_failure() throws Exception {
        when(tokenService.validateAndConsume(anyLong(), any()))
                .thenThrow(new RedisConnectionFailureException("down"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-USER-ID", "1");
        req.addHeader("X-Entry-Token", "any");
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(req, res, new Object())).isFalse();
        assertThat(res.getStatus()).isEqualTo(503);
        assertThat(res.getHeader("Retry-After")).isEqualTo("5");
    }
}
