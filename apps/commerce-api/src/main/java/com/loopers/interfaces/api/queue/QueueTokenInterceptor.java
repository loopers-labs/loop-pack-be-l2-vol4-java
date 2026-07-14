package com.loopers.interfaces.api.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.user.UserModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 주문 생성(POST /api/v1/orders) 앞단에서 입장 토큰을 검증한다.
 * AuthInterceptor가 먼저 실행되어 request 속성에 currentUser를 채워둔 뒤 동작한다는 전제.
 * <p>
 * <b>fail-closed 정책</b>: 대기열은 폭증 트래픽으로부터 주문 경로(DB·재고·결제)를 보호하는 관문이다.
 * Redis 장애로 토큰을 검증할 수 없을 때 우회(fail-open)하면, throttle이 가장 필요한 순간에
 * 보호막이 사라져 Redis 장애가 DB 장애로 전이된다. 따라서 검증 불가 시 주문을 막고 503을 반환한다.
 * 서킷브레이커로 장애가 지속되면 fast-fail시켜(대기열 검증조차 시도하지 않음) 스레드 고갈을 막는다.
 */
@RequiredArgsConstructor
@Component
public class QueueTokenInterceptor implements HandlerInterceptor {

    static final String HEADER_QUEUE_TOKEN = "X-Queue-Token";
    static final String HEADER_RETRY_AFTER = "Retry-After";
    static final int RETRY_AFTER_SECONDS = 5;

    private final EntryTokenRepository entryTokenRepository;
    private final CircuitBreaker entryTokenCircuitBreaker;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true; // 주문 생성만 검증 대상 — 목록/상세 조회는 통과
        }

        UserModel currentUser = (UserModel) request.getAttribute("currentUser");
        String token = request.getHeader(HEADER_QUEUE_TOKEN);
        if (token == null) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "유효한 입장 토큰이 없습니다. 대기열에 먼저 진입해주세요.");
        }

        // Redis 장애/서킷 OPEN 시 fallback에서 503을 던져 주문을 막는다(fail-closed).
        // 정상 응답(true/false)은 fallback을 타지 않으므로, 토큰 불일치는 그대로 아래 401로 흐른다.
        boolean valid = entryTokenCircuitBreaker.run(
            () -> entryTokenRepository.isValid(currentUser.getId(), token),
            throwable -> { throw queueUnavailable(response); }
        );

        if (!valid) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "유효한 입장 토큰이 없습니다. 대기열에 먼저 진입해주세요.");
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return;
        }
        if (HttpStatus.valueOf(response.getStatus()).is2xxSuccessful()) {
            UserModel currentUser = (UserModel) request.getAttribute("currentUser");
            entryTokenRepository.delete(currentUser.getId());
        }
    }

    private CoreException queueUnavailable(HttpServletResponse response) {
        response.setHeader(HEADER_RETRY_AFTER, String.valueOf(RETRY_AFTER_SECONDS));
        return new CoreException(ErrorType.SERVICE_UNAVAILABLE,
            "대기열 시스템이 일시적으로 불가합니다. 잠시 후 다시 시도해주세요.");
    }
}
