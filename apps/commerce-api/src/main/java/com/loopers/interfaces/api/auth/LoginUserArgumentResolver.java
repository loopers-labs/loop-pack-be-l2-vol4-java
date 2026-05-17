package com.loopers.interfaces.api.auth;

import com.loopers.application.user.UserFacade;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    public static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final UserFacade userFacade;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class) && parameter.getParameterType().equals(AuthUser.class);
    }

    @Override
    public Object resolveArgument(
        MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory
    ) {
        String loginId = requiredHeader(webRequest, HEADER_LOGIN_ID);
        String loginPw = requiredHeader(webRequest, HEADER_LOGIN_PW);
        Long userId = userFacade.authenticate(loginId, loginPw);
        return new AuthUser(userId, loginId);
    }

    private String requiredHeader(NativeWebRequest webRequest, String name) {
        return Optional.ofNullable(webRequest.getHeader(name))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "인증 헤더가 누락되었습니다."));
    }
}
