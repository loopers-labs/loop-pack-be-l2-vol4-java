package com.loopers.support.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoreExceptionTest {
    @DisplayName("ErrorType 기반의 예외 생성 시, 별도의 메시지가 주어지지 않으면 ErrorType의 메시지를 사용한다.")
    @Test
    void messageShouldBeErrorTypeMessage_whenCustomMessageIsNull() {
        // arrange
        ErrorType[] errorTypes = ErrorType.values();

        // act & assert
        for (ErrorType errorType : errorTypes) {
            CoreException exception = new CoreException(errorType);
            assertThat(exception.getMessage()).isEqualTo(errorType.getMessage());
        }
    }

    @DisplayName("ErrorType 기반의 예외 생성 시, 별도의 메시지가 주어지면 해당 메시지를 사용한다.")
    @Test
    void messageShouldBeCustomMessage_whenCustomMessageIsNotNull() {
        // arrange
        String customMessage = "custom message";

        // act
        CoreException exception = new CoreException(ErrorType.INTERNAL_ERROR, customMessage);

        // assert
        assertThat(exception.getMessage()).isEqualTo(customMessage);
    }

    @DisplayName("ErrorType과 cause로 생성 시, customMessage가 null이면 ErrorType의 메시지를 사용하고 cause는 보존된다.")
    @Test
    void preservesCauseAndUsesErrorTypeMessage_whenCustomMessageIsNullAndCauseGiven() {
        // arrange
        RuntimeException rootCause = new RuntimeException("root cause");

        // act
        CoreException exception = new CoreException(ErrorType.INTERNAL_ERROR, null, rootCause);

        // assert
        assertThat(exception.getCause()).isSameAs(rootCause);
        assertThat(exception.getMessage()).isEqualTo(ErrorType.INTERNAL_ERROR.getMessage());
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
        assertThat(exception.getCustomMessage()).isNull();
    }

    @DisplayName("ErrorType, customMessage, cause로 생성 시, customMessage가 사용되고 cause는 보존된다.")
    @Test
    void preservesCauseAndUsesCustomMessage_whenCustomMessageAndCauseGiven() {
        // arrange
        String customMessage = "custom message";
        RuntimeException rootCause = new RuntimeException("root cause");

        // act
        CoreException exception = new CoreException(ErrorType.CONFLICT, customMessage, rootCause);

        // assert
        assertThat(exception.getCause()).isSameAs(rootCause);
        assertThat(exception.getMessage()).isEqualTo(customMessage);
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        assertThat(exception.getCustomMessage()).isEqualTo(customMessage);
    }
}
