package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LikesCursorTest {

    @DisplayName("encode/decode 라운드트립이 보존된다")
    @Test
    void encode_decode_round_trip() {
        LikesCursor cursor = new LikesCursor(30L, 3L);

        LikesCursor back = LikesCursor.decode(cursor.encode());

        assertThat(back).isEqualTo(cursor);
    }

    @DisplayName("null/빈 토큰은 첫 페이지(null)로 해석한다")
    @Test
    void blank_token_means_first_page() {
        assertThat(LikesCursor.decode(null)).isNull();
        assertThat(LikesCursor.decode("")).isNull();
    }

    @DisplayName("형식이 깨진 토큰은 BAD_REQUEST 로 거부한다")
    @Test
    void malformed_token_rejected() {
        assertThatThrownBy(() -> LikesCursor.decode("not-base64-!@#"))
            .isInstanceOf(CoreException.class);
    }
}
