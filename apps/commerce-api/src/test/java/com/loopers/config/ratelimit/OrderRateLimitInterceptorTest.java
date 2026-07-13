package com.loopers.config.ratelimit;

import com.loopers.domain.ratelimit.RateLimitRedisStore;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * OrderRateLimitInterceptor 단위 테스트.
 *
 * <p>Redis 연동 없이 {@link RateLimitRedisStore} 를 목으로 대체해 POST/GET 분기와
 * 한도 초과 시 예외 매핑만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class OrderRateLimitInterceptorTest {

    @Mock private RateLimitRedisStore rateLimitRedisStore;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    @DisplayName("POST 요청에서 저장소가 false 를 반환하면 TOO_MANY_REQUESTS 예외를 던진다")
    @Test
    void throwsTooManyRequests_whenStoreReturnsFalse_forPostRequest() {
        // Arrange
        OrderRateLimitInterceptor interceptor = new OrderRateLimitInterceptor(rateLimitRedisStore);
        ReflectionTestUtils.setField(interceptor, "limit", 100);
        ReflectionTestUtils.setField(interceptor, "windowSeconds", 1);
        when(request.getMethod()).thenReturn("POST");
        when(rateLimitRedisStore.tryAcquire(anyString(), anyInt(), anyInt())).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
            .isInstanceOf(CoreException.class)
            .extracting(ex -> ((CoreException) ex).getErrorType())
            .isEqualTo(ErrorType.TOO_MANY_REQUESTS);
    }

    @DisplayName("GET 요청은 저장소 결과와 무관하게 예외 없이 true 를 반환한다")
    @Test
    void returnsTrueWithoutThrowing_forGetRequest_regardlessOfStoreResult() {
        // Arrange
        OrderRateLimitInterceptor interceptor = new OrderRateLimitInterceptor(rateLimitRedisStore);
        ReflectionTestUtils.setField(interceptor, "limit", 100);
        ReflectionTestUtils.setField(interceptor, "windowSeconds", 1);
        when(request.getMethod()).thenReturn("GET");

        // Act
        boolean result = interceptor.preHandle(request, response, new Object());

        // Assert
        assertThat(result).isTrue();
    }
}
