package com.loopers.interfaces.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.domain.user.enums.UserRole;
import com.loopers.domain.user.vo.UserId;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/api-admin")) return false;
        if (path.endsWith("/likes")) return false;
        if (path.endsWith("/me")) return false;
        if (path.endsWith("/password")) return false;
        if (path.startsWith("/api/v1/orders")) return false;
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String loginId = request.getHeader("X-Loopers-LoginId");
        String loginPw = request.getHeader("X-Loopers-LoginPw");

        if (loginId == null || loginId.isBlank() || loginPw == null || loginPw.isBlank()) {
            writeUnauthorized(response, "로그인 정보가 필요합니다.");
            return;
        }

        Optional<UserModel> userOpt = userRepository.findByUserId(new UserId(loginId))
                .filter(u -> passwordEncoder.matches(loginPw, u.getPassword().getValue()));

        if (userOpt.isEmpty()) {
            writeUnauthorized(response, "아이디 또는 비밀번호가 올바르지 않습니다.");
            return;
        }

        UserModel user = userOpt.get();

        if (request.getRequestURI().startsWith("/api-admin") && user.getRole() != UserRole.ADMIN) {
            writeForbidden(response, "관리자 권한이 필요합니다.");
            return;
        }

        request.setAttribute("authenticatedUserId", user.getId());
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        writeError(response, ErrorType.UNAUTHORIZED, message);
    }

    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        writeError(response, ErrorType.FORBIDDEN, message);
    }

    private void writeError(HttpServletResponse response, ErrorType errorType, String message) throws IOException {
        response.setStatus(errorType.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(errorType.getCode(), message)));
    }
}
