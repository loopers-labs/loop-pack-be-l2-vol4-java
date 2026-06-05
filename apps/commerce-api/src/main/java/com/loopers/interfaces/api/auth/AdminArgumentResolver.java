package com.loopers.interfaces.api.auth;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Optional;

/** 어드민(/api-admin) 인증 — X-Loopers-Ldap 헤더로 어드민을 식별한다. */
@Component
public class AdminArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String HEADER_LDAP = "X-Loopers-Ldap";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LdapAdmin.class) && parameter.getParameterType().equals(AdminUser.class);
    }

    @Override
    public Object resolveArgument(
        MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory
    ) {
        String ldap = Optional.ofNullable(webRequest.getHeader(HEADER_LDAP))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "어드민 인증 헤더가 누락되었습니다."));
        return new AdminUser(ldap);
    }
}
