package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueFacade;
import com.loopers.interfaces.api.user.LoginInterceptor;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@RequiredArgsConstructor
@Component
public class QueueTokenInterceptor implements HandlerInterceptor {

    public static final String ENTRY_TOKEN_HEADER = "X-Entry-Token";

    private final QueueFacade queueFacade;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }

        Long userId = (Long) request.getAttribute(LoginInterceptor.USER_ID_ATTRIBUTE);
        String token = request.getHeader(ENTRY_TOKEN_HEADER);

        if (token == null || token.isBlank()) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "입장 토큰이 필요합니다.");
        }
        if (!queueFacade.validateToken(userId, token)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "입장 토큰이 유효하지 않거나 만료되었습니다.");
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return;
        }
        if (response.getStatus() >= 200 && response.getStatus() < 300) {
            Long userId = (Long) request.getAttribute(LoginInterceptor.USER_ID_ATTRIBUTE);
            queueFacade.completeOrder(userId);
        }
    }
}
