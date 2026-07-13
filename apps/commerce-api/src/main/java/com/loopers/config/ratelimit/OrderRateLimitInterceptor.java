package com.loopers.config.ratelimit;

import com.loopers.domain.ratelimit.RateLimitRedisStore;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 주문 생성(POST /api/v1/orders) 요청에만 Redis 고정 윈도우 카운터로 처리량을 제한한다.
 *
 * <p>같은 경로에 매핑된 조회(GET)는 대상이 아니다 — 읽기는 과부하의 주된 원인이 아니고,
 * 주문 폭주 시 보호가 필요한 것은 쓰기(재고 차감/결제 트리거) 경로이기 때문이다.
 *
 * <p><strong>Redis 장애 시 fail-open</strong>: 레이트리미터는 어디까지나 보조 방어선이고,
 * 실제 정합성은 {@code bindResources}의 원자적 재고 차감이 지킨다. Redis 장애로 판정 자체가
 * 불가능해졌다고 정상 주문까지 막으면 본말전도이므로, 판정 실패 시 통과시키고 로그만 남긴다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class OrderRateLimitInterceptor implements HandlerInterceptor {

    private static final String RATE_LIMIT_KEY = "ratelimit:orders";

    private final RateLimitRedisStore rateLimitRedisStore;
    private final RateLimitProperties rateLimitProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }

        boolean acquired;
        try {
            acquired = rateLimitRedisStore.tryAcquire(
                RATE_LIMIT_KEY, rateLimitProperties.limit(), rateLimitProperties.windowSeconds());
        } catch (Exception e) {
            log.warn("[RateLimit] Redis 오류로 판정 불가 — fail-open으로 통과시킨다.", e);
            return true;
        }

        if (!acquired) {
            throw new CoreException(ErrorType.TOO_MANY_REQUESTS, "주문 요청이 많습니다. 잠시 후 다시 시도해주세요.");
        }
        return true;
    }
}
