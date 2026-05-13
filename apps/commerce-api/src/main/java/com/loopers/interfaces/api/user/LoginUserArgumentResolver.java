package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@RequiredArgsConstructor
@Component
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";

    private final UserFacade userFacade;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class)
            && UserInfo.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
        MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory
    ) {
        String loginId = webRequest.getHeader(LOGIN_ID_HEADER);
        String rawPassword = webRequest.getHeader(LOGIN_PW_HEADER);
        if (loginId == null || loginId.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증 헤더가 필요합니다.");
        }
        return userFacade.authenticate(loginId, rawPassword);
    }
}
