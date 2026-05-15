package com.loopers.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
@Component
public class LoopersAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    public static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final UserAuthenticator userAuthenticator;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        authenticate(request.getHeader(HEADER_LOGIN_ID), request.getHeader(HEADER_LOGIN_PW));

        filterChain.doFilter(request, response);
    }

    private void authenticate(String loginId, String rawPassword) {
        userAuthenticator.authenticate(loginId, rawPassword)
            .ifPresent(this::setAuthentication);
    }

    private void setAuthentication(Long userId) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            userId,
            null,
            List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
