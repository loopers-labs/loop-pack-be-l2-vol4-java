package com.loopers.interfaces.api.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

public class LoginInterceptor implements HandlerInterceptor {

    public static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    public static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String loginId = request.getHeader(LOGIN_ID_HEADER);
        String password = request.getHeader(LOGIN_PW_HEADER);

        if (loginId == null || password == null) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증 헤더가 필요합니다.");
        }

        request.setAttribute("loginId", loginId);
        request.setAttribute("password", password);
        return true;
    }
}
