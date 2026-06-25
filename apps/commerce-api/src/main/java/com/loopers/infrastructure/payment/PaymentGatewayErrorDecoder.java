package com.loopers.infrastructure.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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
        // 404는 우리 도메인 의미(BAD_REQUEST)로 단정하지 않고, Feign이 제공하는 타입(FeignException.NotFound)으로 그대로 흘려보낸다.
        if (response.status() == 404) {
            return new ErrorDecoder.Default().decode(methodKey, response);
        }

        String message = extractMessage(response);

        if (response.status() >= 400 && response.status() < 500) {
            return new CoreException(ErrorType.BAD_REQUEST, message);
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
