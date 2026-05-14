package com.loopers.interfaces.api;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class AuthHeadersArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(AuthHeaders.class);
    }

    @Override
    public Object resolveArgument(
        MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory
    ) throws Exception {
        String loginId = webRequest.getHeader(HEADER_LOGIN_ID);
        if (loginId == null) {
            throw new MissingRequestHeaderException(HEADER_LOGIN_ID, parameter);
        }
        String loginPw = webRequest.getHeader(HEADER_LOGIN_PW);
        if (loginPw == null) {
            throw new MissingRequestHeaderException(HEADER_LOGIN_PW, parameter);
        }
        return new AuthHeaders(loginId, loginPw);
    }
}
