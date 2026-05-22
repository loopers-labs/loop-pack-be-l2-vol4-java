package com.loopers.support.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증되지 않은 요청이 보호 경로에 접근했을 때 401 응답을 ApiResponse JSON 으로 내려준다.
 */
@RequiredArgsConstructor
@Component
public class UnauthorizedEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(ErrorType.UNAUTHORIZED.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Object> body = ApiResponse.fail(
            ErrorType.UNAUTHORIZED.getCode(),
            ErrorType.UNAUTHORIZED.getMessage()
        );
        objectMapper.writeValue(response.getWriter(), body);
    }
}
