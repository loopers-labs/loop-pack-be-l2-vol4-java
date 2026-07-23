package com.loopers.tddstudy.interfaces.api.order;

import com.loopers.tddstudy.application.queue.EntryTokenService;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class EntryTokenInterceptor implements HandlerInterceptor {

    private final EntryTokenService entryTokenService;

    public EntryTokenInterceptor(EntryTokenService entryTokenService) {
        this.entryTokenService = entryTokenService;
    }

    // 컨트롤러 실행 "전"에 호출됨. false 반환 = 요청 차단

    @Override
    public boolean preHandle(@Nonnull HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String userIdHeader = request.getHeader("X-USER-ID");
        String token = request.getHeader("X-Entry-Token");

        if (userIdHeader == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);   // 401
            return false;
        }
        Long userId = Long.valueOf(userIdHeader);

        try {
            if (entryTokenService.validateAndConsume(userId, token)) {
                return true;                                            // ✅ 통과 (토큰 소모됨)
            }
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);       // 403 토큰 없음/무효
            return false;
        } catch (RedisConnectionFailureException e) {
            // Graceful Degradation: Redis 장애 → 전면 차단(503) + 부드러운 재시도 유도
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);   // 503
            response.setHeader("Retry-After", "5");                           // 5초 뒤 재시도 힌트
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"message\":\"잠시 혼잡합니다. 자동으로 다시 시도 중이에요.\"}");
            return false;
        }
    }
}
