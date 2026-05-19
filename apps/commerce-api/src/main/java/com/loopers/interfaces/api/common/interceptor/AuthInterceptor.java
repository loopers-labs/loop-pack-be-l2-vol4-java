package com.loopers.interfaces.api.common.interceptor;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String AUTHENTICATED_USER = "authenticatedUser";
    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[A-Za-z0-9]{1,10}$");

    private final UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String loginId = request.getHeader("X-Loopers-LoginId");
        String password = request.getHeader("X-Loopers-LoginPw");

        if (!StringUtils.hasText(loginId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "필수 요청 헤더 'X-Loopers-LoginId'가 누락되었습니다.");
        }
        if (!LOGIN_ID_PATTERN.matcher(loginId).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문/숫자 1~10자여야 합니다.");
        }
        if (!StringUtils.hasText(password)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "필수 요청 헤더 'X-Loopers-LoginPw'가 누락되었습니다.");
        }

        UserModel user = userService.authenticate(loginId, password);
        request.setAttribute(AUTHENTICATED_USER, user);
        return true;
    }
}
