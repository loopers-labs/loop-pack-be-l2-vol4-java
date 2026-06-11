package com.loopers.interfaces.api;

import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class ApiControllerAdviceTest {

    private final ApiControllerAdvice advice = new ApiControllerAdvice();

    @DisplayName("낙관적 락 충돌(ObjectOptimisticLockingFailureException)은 409 CONFLICT · CONCURRENCY_CONFLICT 로 매핑된다 (500 아님).")
    @Test
    void mapsOptimisticLockFailureToConflict() {
        // given
        ObjectOptimisticLockingFailureException exception =
            new ObjectOptimisticLockingFailureException("user_coupon", 1L);

        // when
        ResponseEntity<ApiResponse<?>> response = advice.handle(exception);

        // then
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
            () -> assertThat(response.getBody()).isNotNull(),
            () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
            () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.CONCURRENCY_CONFLICT.getCode())
        );
    }

    @DisplayName("비관적 락 충돌(CannotAcquireLockException)도 같은 핸들러로 409 CONFLICT · CONCURRENCY_CONFLICT 로 매핑된다.")
    @Test
    void mapsPessimisticLockFailureToConflict() {
        // given
        CannotAcquireLockException exception = new CannotAcquireLockException("lock wait timeout");

        // when
        ResponseEntity<ApiResponse<?>> response = advice.handle(exception);

        // then
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
            () -> assertThat(response.getBody()).isNotNull(),
            () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.CONCURRENCY_CONFLICT.getCode())
        );
    }
}
