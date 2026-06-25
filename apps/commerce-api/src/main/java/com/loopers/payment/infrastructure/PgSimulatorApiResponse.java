package com.loopers.payment.infrastructure;

record PgSimulatorApiResponse<T>(
    PgSimulatorMetadata meta,
    T data
) {

    boolean isSuccess() {
        return data != null && meta != null && meta.isSuccess();
    }

    String message() {
        return meta == null ? null : meta.message();
    }
}
