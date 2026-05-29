package com.loopers.interfaces.api.common.interceptor;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 어드민 API 인증 인터셉터.
 * X-Loopers-Ldap: loopers.admin 헤더 값 일치 여부만 검증한다. (실제 LDAP 미연동)
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String LDAP_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP_VALUE = "loopers.admin";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String ldap = request.getHeader(LDAP_HEADER);
        if (!ADMIN_LDAP_VALUE.equals(ldap)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "관리자 인증에 실패했습니다.");
        }
        return true;
    }
}
