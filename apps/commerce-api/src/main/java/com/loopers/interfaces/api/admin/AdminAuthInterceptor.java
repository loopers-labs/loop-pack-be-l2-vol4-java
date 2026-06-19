package com.loopers.interfaces.api.admin;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * ldap_required 대체: 실제 LDAP 연동 없이 관리자 헤더 존재만 검증한다.
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    public static final String HEADER_LDAP = "X-Loopers-Ldap";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String ldap = request.getHeader(HEADER_LDAP);
        if (ldap == null || ldap.isBlank()) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "관리자 인증 헤더(" + HEADER_LDAP + ")가 필요합니다.");
        }
        return true;
    }
}
