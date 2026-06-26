package com.loopers.interfaces.auth;

import com.loopers.application.user.UserApplicationService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.HandlerInterceptor;

@RequiredArgsConstructor
public class UserAuthInterceptor implements HandlerInterceptor {

    private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    private static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";

    private final UserApplicationService userApplicationService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String loginId = request.getHeader(LOGIN_ID_HEADER);
        if (loginId == null) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "X-Loopers-LoginId 헤더가 필요합니다.");
        }

        String loginPw = request.getHeader(LOGIN_PW_HEADER);
        if (loginPw == null) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "X-Loopers-LoginPw 헤더가 필요합니다.");
        }

        String userId = userApplicationService.authenticate(loginId, loginPw);
        request.setAttribute(LoginUserArgumentResolver.USER_ID_ATTRIBUTE, userId);
        return true;
    }
}
