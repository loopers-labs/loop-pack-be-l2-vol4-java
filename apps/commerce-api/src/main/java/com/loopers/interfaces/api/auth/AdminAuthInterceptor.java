package com.loopers.interfaces.api.auth;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String LDAP_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP = "loopers.admin";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String ldap = request.getHeader(LDAP_HEADER);

        if (!ADMIN_LDAP.equals(ldap)) {
            throw new CoreException(ErrorType.FORBIDDEN);
        }

        return true;
    }
}
