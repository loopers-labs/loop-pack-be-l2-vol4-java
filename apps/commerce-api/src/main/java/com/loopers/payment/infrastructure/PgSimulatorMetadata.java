package com.loopers.payment.infrastructure;

record PgSimulatorMetadata(
    PgSimulatorResult result,
    String errorCode,
    String message
) {

    boolean isSuccess() {
        return result == PgSimulatorResult.SUCCESS;
    }
}
