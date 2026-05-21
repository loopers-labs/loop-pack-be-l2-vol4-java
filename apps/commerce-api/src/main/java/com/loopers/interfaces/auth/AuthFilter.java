package com.loopers.interfaces.auth;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
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

    private final UserService userService;
    private final AuthErrorResponseWriter authErrorResponseWriter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !AUTH_REQUIRED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String loginId = request.getHeader(LOGIN_ID_HEADER);
            String password = request.getHeader(LOGIN_PASSWORD_HEADER);
            UserModel user = userService.authenticate(loginId, password);
            request.setAttribute(LOGIN_USER_ATTRIBUTE, AuthenticatedUser.from(user));
            filterChain.doFilter(request, response);
        } catch (CoreException e) {
            authErrorResponseWriter.write(response, e);
        }
    }
}
