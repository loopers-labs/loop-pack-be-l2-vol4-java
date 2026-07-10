package com.loopers.config.ratelimit;

import com.loopers.domain.ratelimit.RateLimitRedisStore;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 주문 생성(POST /api/v1/orders) 요청에만 Redis 고정 윈도우 카운터로 처리량을 제한한다.
 *
 * <p>같은 경로에 매핑된 조회(GET)는 대상이 아니다 — 읽기는 과부하의 주된 원인이 아니고,
 * 주문 폭주 시 보호가 필요한 것은 쓰기(재고 차감/결제 트리거) 경로이기 때문이다.
 */
@RequiredArgsConstructor
@Component
public class OrderRateLimitInterceptor implements HandlerInterceptor {

    private static final String RATE_LIMIT_KEY = "ratelimit:orders";

    private final RateLimitRedisStore rateLimitRedisStore;

    @Value("${ratelimit.orders.limit:100}")
    private int limit;

    @Value("${ratelimit.orders.window-seconds:1}")
    private int windowSeconds;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        if (!rateLimitRedisStore.tryAcquire(RATE_LIMIT_KEY, limit, windowSeconds)) {
            throw new CoreException(ErrorType.TOO_MANY_REQUESTS, "주문 요청이 많습니다. 잠시 후 다시 시도해주세요.");
        }
        return true;
    }
}
