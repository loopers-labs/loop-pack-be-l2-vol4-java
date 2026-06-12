package com.loopers.config.security;

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

@RequiredArgsConstructor
@Component
public class LoopersAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) throws IOException {
        response.setStatus(ErrorType.UNAUTHORIZED.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<?> body = ApiResponse.fail(ErrorType.UNAUTHORIZED.getCode(), ErrorType.UNAUTHORIZED.getMessage());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
