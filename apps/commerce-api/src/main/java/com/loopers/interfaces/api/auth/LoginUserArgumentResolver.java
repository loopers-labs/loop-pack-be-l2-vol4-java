package com.loopers.interfaces.api.auth;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class)
            && parameter.getParameterType().equals(LoginCredentials.class);
    }

    @Override
    public Object resolveArgument(
        MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory
    ) {
        String loginId = webRequest.getHeader(HEADER_LOGIN_ID);
        String loginPw = webRequest.getHeader(HEADER_LOGIN_PW);
        if (loginId == null || loginId.isBlank() || loginPw == null || loginPw.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 헤더가 누락되었습니다.");
        }
        return new LoginCredentials(loginId, loginPw);
    }
}
