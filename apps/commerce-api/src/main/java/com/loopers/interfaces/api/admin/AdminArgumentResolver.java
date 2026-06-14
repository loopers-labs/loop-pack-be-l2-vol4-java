package com.loopers.interfaces.api.admin;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * @Admin 파라미터를 해석한다. X-Loopers-Ldap 헤더가 없으면 인증 실패.
 */
@Component
public class AdminArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String LDAP_HEADER = "X-Loopers-Ldap";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(Admin.class);
    }

    @Override
    public Object resolveArgument(
        MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory
    ) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        String ldap = request.getHeader(LDAP_HEADER);
        if (ldap == null || ldap.isBlank()) {
            throw new CoreException(ErrorType.UNAUTHORIZED);
        }
        return ldap;
    }
}
