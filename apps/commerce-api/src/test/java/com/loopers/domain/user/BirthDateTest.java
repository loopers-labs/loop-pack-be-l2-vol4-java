package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BirthDateTest {
    @DisplayName("생년월일을 생성할 때, ")
    @Nested
    class Create {
        @DisplayName("정상적으로 생성된다.")
        @Test
        void createBirthDate() {
            // given
            String str = "1998-06-12";

            // when
            BirthDate birthDate = new BirthDate(str);

            // then
            assertThat(birthDate.value()).isEqualTo(str);
        }

        @DisplayName("yyyy-MM-dd 형식에 맞지 않으면 BAD_REQUEST 예외가 발생한다")
        @NullAndEmptySource
        @ValueSource(strings = {"19900101", "1990/01/01", "90-01-01", "1990-1-1"})
        @ParameterizedTest
        void throwsBadRequestException_whenBirthDateIsInvalid(String birthDate) {
            // when
            CoreException result = assertThrows(CoreException.class, () -> new BirthDate(birthDate));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

}
