package com.loopers.interfaces.api.user;

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
public class LoginInterceptor implements HandlerInterceptor {

    public static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    public static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";
    public static final String USER_ID_ATTRIBUTE = "userId";

    private final UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String loginId = request.getHeader(LOGIN_ID_HEADER);
        String password = request.getHeader(LOGIN_PW_HEADER);

        if (loginId == null || loginId.isBlank() || password == null || password.isBlank()) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증 헤더가 필요합니다.");
        }

        // 인증 완료 후 userId만 attribute로 전달 (비밀번호 제거)
        UserModel user = userService.getUser(loginId, password);
        request.setAttribute(USER_ID_ATTRIBUTE, user.getId());

        return true;
    }
}
