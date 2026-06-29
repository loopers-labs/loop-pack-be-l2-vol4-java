package com.loopers.payment.infrastructure.pg;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import feign.Response;
import feign.codec.ErrorDecoder;

public class PgErrorDecoder implements ErrorDecoder {

    @Override
    public Exception decode(String methodKey, Response response) {
        ErrorType errorType = switch (response.status()) {
            case 400 -> ErrorType.BAD_REQUEST;
            case 404 -> ErrorType.NOT_FOUND;
            default -> ErrorType.INTERNAL_ERROR;
        };
        return new CoreException(errorType, "PG 응답 오류 (status: " + response.status() + ")");
    }
}
