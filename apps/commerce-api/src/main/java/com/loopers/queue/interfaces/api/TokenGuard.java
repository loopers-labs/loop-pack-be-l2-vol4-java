package com.loopers.queue.interfaces.api;

import com.loopers.queue.domain.EntryTokenStore;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 주문 진입 관문. POST 주문 요청에 유효한 입장 토큰(X-Entry-Token)이 있는지 검증한다.
 * 검증만 하고 삭제하지 않는다 — 삭제는 주문 성공 이벤트에서 처리한다.
 */
@Component
@RequiredArgsConstructor
public class TokenGuard implements HandlerInterceptor {

    public static final String HEADER = "X-Entry-Token";

    private final EntryTokenStore entryTokenStore;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        String presented = request.getHeader(HEADER);
        if (presented == null || presented.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "입장 토큰이 필요합니다.");
        }
        String userId = String.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        String stored = entryTokenStore.find(userId).orElse(null);
        if (!presented.equals(stored)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "입장 토큰이 유효하지 않습니다.");
        }
        return true;
    }
}
