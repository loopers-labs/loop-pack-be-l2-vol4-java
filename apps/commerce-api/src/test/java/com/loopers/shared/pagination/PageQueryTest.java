package com.loopers.shared.pagination;

import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageQueryTest {

    @DisplayName("page 가 음수이면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenPageIsNegative() {
        // act & assert
        assertThatThrownBy(() -> new PageQuery(-1, 20))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("size 가 0 이하이면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenSizeIsZeroOrNegative() {
        // act & assert
        assertThatThrownBy(() -> new PageQuery(0, 0))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }
}
