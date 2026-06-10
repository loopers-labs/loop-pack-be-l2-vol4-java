package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SortOptionTest {

    @DisplayName("정렬 옵션 변환 시 입력이 null이거나 공백이면 LATEST로 변환된다")
    @Test
    void returnsLatest_whenInputIsNullOrBlank() {
        assertThat(SortOption.from(null)).isEqualTo(SortOption.LATEST);
        assertThat(SortOption.from("")).isEqualTo(SortOption.LATEST);
        assertThat(SortOption.from("   ")).isEqualTo(SortOption.LATEST);
    }

    @DisplayName("정렬 옵션 변환 시 유효한 값을 대소문자 무관하게 변환한다")
    @Test
    void returnsMatchingOption_whenInputIsValid() {
        assertThat(SortOption.from("latest")).isEqualTo(SortOption.LATEST);
        assertThat(SortOption.from("price_asc")).isEqualTo(SortOption.PRICE_ASC);
        assertThat(SortOption.from("LIKES_DESC")).isEqualTo(SortOption.LIKES_DESC);
    }

    @DisplayName("정렬 옵션 변환 시 알 수 없는 값이면 BAD_REQUEST 예외가 발생한다")
    @Test
    void throwsBadRequest_whenInputIsUnknown() {
        CoreException ex = assertThrows(CoreException.class, () -> SortOption.from("invalid_sort"));
        assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }
}
