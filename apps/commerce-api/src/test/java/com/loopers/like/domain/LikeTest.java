package com.loopers.like.domain;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class LikeTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;

    @Test
    @DisplayName("create 로 생성하면 userId 와 productId 가 저장된다")
    void givenUserIdAndProductId_whenCreate_thenStoresFields() {
        Like like = Like.create(USER_ID, PRODUCT_ID);

        assertAll(
                () -> assertThat(like.getUserId()).isEqualTo(USER_ID),
                () -> assertThat(like.getProductId()).isEqualTo(PRODUCT_ID),
                () -> assertThat(like.getDeletedAt()).isNull()
        );
    }

    @Test
    @DisplayName("userId 가 null 이면 CoreException 이 발생한다")
    void givenNullUserId_whenCreate_thenThrowsCoreException() {
        assertThatThrownBy(() -> Like.create(null, PRODUCT_ID))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("userId 는 비어있을 수 없습니다.");
    }

    @Test
    @DisplayName("productId 가 null 이면 CoreException 이 발생한다")
    void givenNullProductId_whenCreate_thenThrowsCoreException() {
        assertThatThrownBy(() -> Like.create(USER_ID, null))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("productId 는 비어있을 수 없습니다.");
    }

    @Test
    @DisplayName("delete 호출 시 deletedAt 이 채워진다 (좋아요 취소)")
    void givenActiveLike_whenDelete_thenDeletedAtIsSet() {
        Like like = Like.create(USER_ID, PRODUCT_ID);

        like.delete();

        assertThat(like.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("delete 는 멱등하다 (이미 취소된 좋아요를 다시 취소해도 동일)")
    void givenCancelledLike_whenDeleteAgain_thenDeletedAtRemainsUnchanged() {
        Like like = Like.create(USER_ID, PRODUCT_ID);

        like.delete();
        var firstDeletedAt = like.getDeletedAt();
        like.delete();

        assertThat(like.getDeletedAt()).isEqualTo(firstDeletedAt);
    }

    @Test
    @DisplayName("restore 호출 시 deletedAt 이 비워진다 (좋아요 재등록)")
    void givenCancelledLike_whenRestore_thenDeletedAtIsCleared() {
        Like like = Like.create(USER_ID, PRODUCT_ID);
        like.delete();

        like.restore();

        assertThat(like.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("restore 는 멱등하다 (활성 상태에서 호출해도 그대로)")
    void givenActiveLike_whenRestore_thenDeletedAtRemainsNull() {
        Like like = Like.create(USER_ID, PRODUCT_ID);

        like.restore();

        assertThat(like.getDeletedAt()).isNull();
    }
}
