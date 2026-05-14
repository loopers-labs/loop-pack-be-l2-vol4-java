package com.loopers.interfaces.api.auth;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@RequiredArgsConstructor
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String loginId = request.getHeader(HEADER_LOGIN_ID);
        String rawPassword = request.getHeader(HEADER_LOGIN_PW);

        if (loginId == null || rawPassword == null) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증 헤더가 누락되었습니다.");
        }

        UserModel user = userService.authenticate(loginId, rawPassword);
        request.setAttribute("currentUser", user);
        return true;
    }
}
