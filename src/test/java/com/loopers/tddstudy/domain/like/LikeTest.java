package com.loopers.tddstudy.domain.like;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class LikeTest {

    @Test
    @DisplayName("좋아요를 생성할 수 있다")
    void create_like_success() {
        Like like = new Like(1L, 2L);

        assertThat(like.getUserId()).isEqualTo(1L);
        assertThat(like.getProductId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("유저 ID 가 null 이면 예외가 발생한다")
    void create_like_null_user_id_throws_exception() {
        assertThatThrownBy(() -> new Like(null, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유저 ID는 필수입니다.");
    }

    @Test
    @DisplayName("상품 ID 가 null 이면 예외가 발생한다")
    void create_like_null_product_id_throws_exception() {
        assertThatThrownBy(() -> new Like(1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("상품 ID는 필수입니다.");
    }
}
