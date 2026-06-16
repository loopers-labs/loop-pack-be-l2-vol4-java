package com.loopers.interfaces.auth;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String LDAP_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP_VALUE = "loopers.admin";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String ldap = request.getHeader(LDAP_HEADER);
        if (!ADMIN_LDAP_VALUE.equals(ldap)) {
            throw new CoreException(ErrorType.FORBIDDEN, "관리자 권한이 없습니다.");
        }
        return true;
    }
}
