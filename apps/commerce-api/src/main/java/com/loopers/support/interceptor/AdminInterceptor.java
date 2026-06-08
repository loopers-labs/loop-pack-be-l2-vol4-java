package com.loopers.support.interceptor;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminInterceptor implements HandlerInterceptor {

    private static final String LDAP_HEADER = "X-Loopers-Ldap";
    private static final String LDAP_VALUE = "loopers.admin";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String ldap = request.getHeader(LDAP_HEADER);
        if (!LDAP_VALUE.equals(ldap)) {
            throw new CoreException(ErrorType.UNAUTHORIZED);
        }
        return true;
    }
}
