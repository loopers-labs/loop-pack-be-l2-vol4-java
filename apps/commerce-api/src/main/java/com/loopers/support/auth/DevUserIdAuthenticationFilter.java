package com.loopers.support.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * 부하테스트/개발 전용. X-USER-ID 헤더 값을 그대로 principal(userId)로 신뢰한다 —
 * 비밀번호 검증(BCrypt·DB)을 생략해 "로그인된 세션" 상태를 시뮬레이션한다.
 * auth.dev-user-header.enabled=true 일 때만 SecurityConfig 가 체인에 넣는다(기본 꺼짐).
 */
public class DevUserIdAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-USER-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String userId = request.getHeader(HEADER);

        if (userId != null) {
            try {
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(Long.parseLong(userId), null, Collections.emptyList());
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            } catch (NumberFormatException ignored) {
                // 숫자가 아니면 인증을 세팅하지 않고 통과 — 보호 경로의 401 처리는 인가 계층에 맡긴다
            }
        }

        filterChain.doFilter(request, response);
    }
}
