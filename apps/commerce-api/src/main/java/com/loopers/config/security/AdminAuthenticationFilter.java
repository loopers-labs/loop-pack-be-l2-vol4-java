package com.loopers.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
@Component
public class AdminAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_ADMIN_LDAP = "X-Loopers-Ldap";
    public static final String ADMIN_LDAP = "loopers.admin";

    private static final String ADMIN_PATH = "/api-admin";

    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isAdminRequest(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (!ADMIN_LDAP.equals(request.getHeader(HEADER_ADMIN_LDAP))) {
            writeUnauthorized(response);
            return;
        }

        setAuthentication();
        filterChain.doFilter(request, response);
    }

    private boolean isAdminRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.equals(ADMIN_PATH) || uri.startsWith(ADMIN_PATH + "/");
    }

    private void setAuthentication() {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            "admin",
            null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        ErrorType errorType = ErrorType.UNAUTHORIZED;
        response.setStatus(errorType.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<?> body = ApiResponse.fail(errorType.getCode(), errorType.getMessage());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
