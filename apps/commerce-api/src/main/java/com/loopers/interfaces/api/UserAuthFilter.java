package com.loopers.interfaces.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.user.User;
import com.loopers.application.user.UserService;
import com.loopers.support.error.CoreException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class UserAuthFilter extends OncePerRequestFilter {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    static final String AUTHENTICATED_USER_ATTR = "authenticatedUser";

    private final ObjectMapper objectMapper;
    private final UserService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {

        String loginId = request.getHeader(HEADER_LOGIN_ID);
        String loginPw = request.getHeader(HEADER_LOGIN_PW);

        if (StringUtils.isBlank(loginId) || StringUtils.isBlank(loginPw)) {
            writeUnauthorized(response);
            return;
        }

        try {
            User user = userService.getUser(loginId, loginPw);
            request.setAttribute(AUTHENTICATED_USER_ATTR, user);
        } catch (CoreException e) {
            writeUnauthorized(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        if (uri.startsWith("/api/v1/users/")) {
            return HttpMethod.POST.matches(method);
        }

        if (uri.matches("/api/v1/products/[^/]+/likes")) {
            return false;
        }

        if (uri.startsWith("/api/v1/orders")) {
            return false;
        }

        if (uri.matches("/api/v1/coupons/[^/]+/issue")) {
            return false;
        }

        if (uri.equals("/api/v1/users/me/coupons")) {
            return false;
        }

        if (uri.equals("/api/v1/payments/callback")) {
            return true;
        }

        if (uri.startsWith("/api/v1/payments")) {
            return false;
        }

        return true;
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        var body = ApiResponse.fail("Unauthorized", "인증에 실패했습니다.");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
