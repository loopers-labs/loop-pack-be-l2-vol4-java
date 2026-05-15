package com.loopers.interfaces.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

@RequiredArgsConstructor
@Component
public class AuthErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, CoreException exception) throws IOException {
        ErrorType errorType = exception.getErrorType();
        response.setStatus(errorType.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String message = exception.getCustomMessage() != null ? exception.getCustomMessage() : errorType.getMessage();
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(errorType.getCode(), message));
    }
}
