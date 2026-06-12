package com.loopers.interfaces.auth;

import com.loopers.application.auth.AuthFacade;
import com.loopers.application.auth.AuthenticatedUserInfo;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@RequiredArgsConstructor
@Component
public class AuthFilter extends OncePerRequestFilter {

    public static final String LOGIN_USER_ATTRIBUTE = "LOGIN_USER";
    private static final Set<String> AUTH_REQUIRED_PATHS = Set.of(
        "/api/v1/users/me",
        "/api/v1/users/password"
    );
    private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    private static final String LOGIN_PASSWORD_HEADER = "X-Loopers-LoginPw";
    private static final String ADMIN_LDAP_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP_VALUE = "loopers.admin";

    private final AuthFacade authFacade;
    private final AuthErrorResponseWriter authErrorResponseWriter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isAuthRequired(request);
    }

    private boolean isAuthRequired(HttpServletRequest request) {
        String path = request.getRequestURI();
        return AUTH_REQUIRED_PATHS.contains(path)
            || path.equals("/api/v1/orders")
            || path.matches("^/api/v1/orders/[^/]+$")
            || path.matches("^/api/v1/coupons/[^/]+/issue$")
            || path.equals("/api/v1/users/me/coupons")
            || path.matches("^/api/v1/users/[^/]+/likes$")
            || path.matches("^/api/v1/products/[^/]+/likes$")
            || path.startsWith("/api-admin/v1/");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            if (isAdminPath(request)) {
                authenticateAdmin(request);
                filterChain.doFilter(request, response);
                return;
            }

            String loginId = request.getHeader(LOGIN_ID_HEADER);
            String password = request.getHeader(LOGIN_PASSWORD_HEADER);
            AuthenticatedUserInfo user = authFacade.authenticate(loginId, password);
            request.setAttribute(LOGIN_USER_ATTRIBUTE, new AuthenticatedUser(user.loginId()));
            filterChain.doFilter(request, response);
        } catch (CoreException e) {
            authErrorResponseWriter.write(response, e);
        }
    }

    private boolean isAdminPath(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api-admin/v1/");
    }

    private void authenticateAdmin(HttpServletRequest request) {
        String ldap = request.getHeader(ADMIN_LDAP_HEADER);
        if (!ADMIN_LDAP_VALUE.equals(ldap)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "관리자 인증 정보가 올바르지 않습니다.");
        }
    }
}
