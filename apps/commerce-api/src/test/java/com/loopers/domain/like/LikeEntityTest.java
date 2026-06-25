package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LikeEntityTest {

    private static final String VALID_USER_ID = "1";
    private static final String VALID_PRODUCT_ID = "1";

    @DisplayName("좋아요 생성")
    @Nested
    class Create {

        @DisplayName("유효한 userId와 productId로 생성하면 성공한다.")
        @Test
        void createsLikeEntity_whenRequestIsValid() {
            // act
            LikeEntity like = new LikeEntity(VALID_USER_ID, VALID_PRODUCT_ID);

            // assert
            assertAll(
                    () -> assertEquals(VALID_USER_ID, like.getUserId()),
                    () -> assertEquals(VALID_PRODUCT_ID, like.getProductId())
            );
        }

        @DisplayName("생성 직후 deletedAt이 null이다. (활성 상태)")
        @Test
        void createsLikeEntity_withNullDeletedAt() {
            // act
            LikeEntity like = new LikeEntity(VALID_USER_ID, VALID_PRODUCT_ID);

            // assert
            assertNull(like.getDeletedAt());
        }

        @DisplayName("userId가 null이면 예외가 발생한다.")
        @Test
        void throwsException_whenUserIdIsNull() {
            // act & assert
            assertThrows(CoreException.class, () -> new LikeEntity(null, VALID_PRODUCT_ID));
        }

        @DisplayName("productId가 null이면 예외가 발생한다.")
        @Test
        void throwsException_whenProductIdIsNull() {
            // act & assert
            assertThrows(CoreException.class, () -> new LikeEntity(VALID_USER_ID, null));
        }
    }

    @DisplayName("좋아요 삭제 (Soft Delete)")
    @Nested
    class Delete {

        @DisplayName("delete() 호출 후 deletedAt이 설정된다.")
        @Test
        void setsDeletedAt_whenDeleteCalled() {
            // arrange
            LikeEntity like = new LikeEntity(VALID_USER_ID, VALID_PRODUCT_ID);

            // act
            like.delete();

            // assert
            assertNotNull(like.getDeletedAt());
        }

        @DisplayName("이미 삭제된 상태에서 delete()를 재호출해도 멱등하다.")
        @Test
        void isIdempotent_whenDeleteCalledTwice() {
            // arrange
            LikeEntity like = new LikeEntity(VALID_USER_ID, VALID_PRODUCT_ID);
            like.delete();
            var firstDeletedAt = like.getDeletedAt();

            // act
            like.delete();

            // assert
            assertEquals(firstDeletedAt, like.getDeletedAt());
        }
    }

    @DisplayName("좋아요 복구 (Restore)")
    @Nested
    class Restore {

        @DisplayName("삭제된 좋아요에 restore()를 호출하면 deletedAt이 null이 된다.")
        @Test
        void clearsDeletedAt_whenRestoreCalled() {
            // arrange
            LikeEntity like = new LikeEntity(VALID_USER_ID, VALID_PRODUCT_ID);
            like.delete();

            // act
            like.restore();

            // assert
            assertNull(like.getDeletedAt());
        }

        @DisplayName("활성 상태에서 restore()를 호출해도 멱등하다.")
        @Test
        void isIdempotent_whenRestoreCalledOnActiveEntity() {
            // arrange
            LikeEntity like = new LikeEntity(VALID_USER_ID, VALID_PRODUCT_ID);

            // act
            like.restore();

            // assert
            assertNull(like.getDeletedAt());
        }
    }
}
