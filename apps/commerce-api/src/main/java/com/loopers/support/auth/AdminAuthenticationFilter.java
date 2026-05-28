package com.loopers.support.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * X-Loopers-Admin-Id 헤더로 관리자 인증을 수행하는 필터.
 * 관리자 인증은 LDAP 등 외부 인증 시스템에서 처리된 것으로 가정하며,
 * 게이트웨이가 주입한 헤더의 존재를 신뢰해 ROLE_ADMIN 권한을 부여한다.
 * 헤더가 없으면 컨텍스트를 설정하지 않고 통과시키며, 보호 경로의 401/403 처리는 Security 인가 계층에 맡긴다.
 */
public class AdminAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_ADMIN_ID = "X-Loopers-Admin-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String adminId = request.getHeader(HEADER_ADMIN_ID);

        if (adminId != null && !adminId.isBlank()) {
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                adminId, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
            );
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        }

        filterChain.doFilter(request, response);
    }
}
