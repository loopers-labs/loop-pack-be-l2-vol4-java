package com.loopers.infrastructure.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.payment.FailureReason;
import com.loopers.domain.payment.PaymentGatewayException;
import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.InputStream;

@RequiredArgsConstructor
public class PaymentGatewayErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper;

    @Override
    public Exception decode(String methodKey, Response response) {
        String message = extractMessage(response);

        if (response.status() >= 400 && response.status() < 500) {
            return new PaymentGatewayException(FailureReason.BAD_REQUEST, message);
        }

        return new RetryableException(
                response.status(),
                message,
                response.request().httpMethod(),
                (Long) null,
                response.request()
        );
    }

    private String extractMessage(Response response) {
        if (response.body() == null) {
            return "PG 응답을 처리할 수 없습니다.";
        }
        try (InputStream body = response.body().asInputStream()) {
            ErrorBody errorBody = objectMapper.readValue(body, ErrorBody.class);
            return errorBody.meta() != null && errorBody.meta().message() != null
                    ? errorBody.meta().message()
                    : "PG 요청이 실패했습니다.";
        } catch (IOException e) {
            return "PG 응답을 처리할 수 없습니다.";
        }
    }

    private record ErrorBody(Meta meta) {
        private record Meta(String message) {
        }
    }
}
