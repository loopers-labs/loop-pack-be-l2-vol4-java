package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LikeModelTest {

    private static final Long VALID_USER_ID = 1L;
    private static final Long VALID_PRODUCT_ID = 100L;

    @DisplayName("정상 입력값으로 LikeModel 을 생성할 수 있다.")
    @Test
    void createsLikeModel_withValidUserIdAndProductId() {
        LikeModel like = new LikeModel(VALID_USER_ID, VALID_PRODUCT_ID);

        assertThat(like.getUserId()).isEqualTo(VALID_USER_ID);
        assertThat(like.getProductId()).isEqualTo(VALID_PRODUCT_ID);
    }

    @DisplayName("userId 가 null 이면 BAD_REQUEST.")
    @Test
    void throwsBadRequest_whenUserIdIsNull() {
        CoreException result = assertThrows(CoreException.class, () ->
                new LikeModel(null, VALID_PRODUCT_ID));

        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("productId 가 null 이면 BAD_REQUEST.")
    @Test
    void throwsBadRequest_whenProductIdIsNull() {
        CoreException result = assertThrows(CoreException.class, () ->
                new LikeModel(VALID_USER_ID, null));

        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }
}
