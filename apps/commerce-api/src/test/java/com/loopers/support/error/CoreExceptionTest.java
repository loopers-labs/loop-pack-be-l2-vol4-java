package com.loopers.support.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoreExceptionTest {
    @DisplayName("ErrorType 湲곕컲???덉쇅 ?앹꽦 ?? 蹂꾨룄??硫붿떆吏媛 二쇱뼱吏吏 ?딆쑝硫?ErrorType??硫붿떆吏瑜??ъ슜?쒕떎.")
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

    @DisplayName("ErrorType 湲곕컲???덉쇅 ?앹꽦 ?? 蹂꾨룄??硫붿떆吏媛 二쇱뼱吏硫??대떦 硫붿떆吏瑜??ъ슜?쒕떎.")
    @Test
    void messageShouldBeCustomMessage_whenCustomMessageIsNotNull() {
        // arrange
        String customMessage = "custom message";

        // act
        CoreException exception = new CoreException(ErrorType.INTERNAL_ERROR, customMessage);

        // assert
        assertThat(exception.getMessage()).isEqualTo(customMessage);
    }
}
