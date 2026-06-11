package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@RequiredArgsConstructor
@Component
public class LoginInterceptor implements HandlerInterceptor {

    public static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    public static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";
    public static final String USER_ID_ATTRIBUTE = "userId";

    private final UserFacade userFacade;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String loginId = request.getHeader(LOGIN_ID_HEADER);
        String password = request.getHeader(LOGIN_PW_HEADER);

        if (loginId == null || loginId.isBlank() || password == null || password.isBlank()) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증 헤더가 필요합니다.");
        }

        try {
            UserInfo user = userFacade.getUser(loginId, password);
            request.setAttribute(USER_ID_ATTRIBUTE, user.id());
        } catch (CoreException e) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증에 실패했습니다.");
        }

        return true;
    }
}
