package com.loopers.interfaces.auth;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String ldap = request.getHeader(AuthHeaders.ADMIN_LDAP);
        if (!AuthHeaders.ADMIN_LDAP_VALUE.equals(ldap)) {
            throw new CoreException(ErrorType.UNAUTHORIZED);
        }
        return true;
    }
}
