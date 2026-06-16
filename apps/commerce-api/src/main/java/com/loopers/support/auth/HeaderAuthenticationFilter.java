package com.loopers.support.auth;

import com.loopers.user.application.UserAccountService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * X-Loopers-LoginId / X-Loopers-LoginPw 헤더로 인증을 수행하는 필터.
 * 인증 성공 시 SecurityContext 에 userId 를 principal 로 설정한다.
 * 실패하거나 헤더가 없으면 컨텍스트를 설정하지 않고 통과시키며, 보호 경로의 401 처리는 Security 인가 계층에 맡긴다.
 */
@RequiredArgsConstructor
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    public static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final UserAccountService userAccountService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String loginId = request.getHeader(HEADER_LOGIN_ID);
        String loginPw = request.getHeader(HEADER_LOGIN_PW);

        if (loginId != null && loginPw != null) {
            userAccountService.authenticate(loginId, loginPw).ifPresent(userId -> {
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            });
        }

        filterChain.doFilter(request, response);
    }
}
