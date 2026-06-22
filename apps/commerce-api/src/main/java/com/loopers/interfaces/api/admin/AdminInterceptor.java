package com.loopers.interfaces.api.admin;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminInterceptor implements HandlerInterceptor {

    public static final String ADMIN_HEADER = "X-Loopers-Ldap";
    public static final String ADMIN_HEADER_VALUE = "loopers.admin";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String ldap = request.getHeader(ADMIN_HEADER);

        if (!ADMIN_HEADER_VALUE.equals(ldap)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "관리자 권한이 필요합니다.");
        }

        return true;
    }
}
