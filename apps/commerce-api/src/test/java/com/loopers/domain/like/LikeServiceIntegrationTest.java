package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LikeServiceIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long OTHER_PRODUCT_ID = 20L;

    @Autowired
    private LikeService likeService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요 등록")
    @Nested
    class Like {

        @DisplayName("[ECP] 존재하지 않는 좋아요를 등록하면 새 LikeEntity가 저장된다.")
        @Test
        void savesNewLike_whenNotExists() {
            // act
            LikeEntity result = likeService.like(USER_ID, PRODUCT_ID);

            // assert
            assertAll(
                    () -> assertNotNull(result.getId()),
                    () -> assertEquals(USER_ID, result.getUserId()),
                    () -> assertEquals(PRODUCT_ID, result.getProductId()),
                    () -> assertNull(result.getDeletedAt())
            );
        }

        @DisplayName("[ECP] 이미 active 좋아요가 존재하면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenAlreadyLiked() {
            // arrange
            likeService.like(USER_ID, PRODUCT_ID);

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> likeService.like(USER_ID, PRODUCT_ID));
            assertEquals(ErrorType.CONFLICT, exception.getErrorType());
        }

        @DisplayName("[State Transition] soft-deleted 좋아요가 존재하면 restore되어 deletedAt이 null이 된다.")
        @Test
        void restoresLike_whenSoftDeletedExists() {
            // arrange
            likeService.like(USER_ID, PRODUCT_ID);
            likeService.unlike(USER_ID, PRODUCT_ID);

            // act
            LikeEntity result = likeService.like(USER_ID, PRODUCT_ID);

            // assert
            assertNull(result.getDeletedAt());
        }
    }

    @DisplayName("좋아요 취소")
    @Nested
    class Unlike {

        @DisplayName("[ECP] active 좋아요를 취소하면 deletedAt이 설정된다.")
        @Test
        void softDeletesLike_whenActive() {
            // arrange
            likeService.like(USER_ID, PRODUCT_ID);

            // act
            likeService.unlike(USER_ID, PRODUCT_ID);

            // assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> likeService.unlike(USER_ID, PRODUCT_ID));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }

        @DisplayName("[ECP] 존재하지 않는 좋아요를 취소하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotExists() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> likeService.unlike(USER_ID, PRODUCT_ID));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }
    }

    @DisplayName("좋아요한 상품 목록 조회")
    @Nested
    class GetLikedProducts {

        @DisplayName("[ECP] userId로 조회하면 해당 유저의 active 좋아요 목록이 반환된다.")
        @Test
        void returnsLikedProducts_whenExists() {
            // arrange
            likeService.like(USER_ID, PRODUCT_ID);
            likeService.like(USER_ID, OTHER_PRODUCT_ID);
            likeService.like(OTHER_USER_ID, PRODUCT_ID);

            // act
            Page<LikeEntity> result = likeService.getLikedProducts(USER_ID, PageRequest.of(0, 20));

            // assert
            assertAll(
                    () -> assertEquals(2, result.getTotalElements()),
                    () -> assertTrue(result.getContent().stream()
                            .allMatch(like -> like.getUserId().equals(USER_ID)))
            );
        }

        @DisplayName("[Error Guessing] 취소된 좋아요는 목록에 포함되지 않는다.")
        @Test
        void excludesUnlikedProducts() {
            // arrange
            likeService.like(USER_ID, PRODUCT_ID);
            likeService.like(USER_ID, OTHER_PRODUCT_ID);
            likeService.unlike(USER_ID, PRODUCT_ID);

            // act
            Page<LikeEntity> result = likeService.getLikedProducts(USER_ID, PageRequest.of(0, 20));

            // assert
            assertAll(
                    () -> assertEquals(1, result.getTotalElements()),
                    () -> assertEquals(OTHER_PRODUCT_ID, result.getContent().get(0).getProductId())
            );
        }
    }

    @DisplayName("상품 연쇄 삭제 (단건)")
    @Nested
    class DeleteAllByProduct {

        @DisplayName("[State Transition] productId에 해당하는 active 좋아요가 모두 soft delete된다.")
        @Test
        void softDeletesAllLikes_byProductId() {
            // arrange
            likeService.like(USER_ID, PRODUCT_ID);
            likeService.like(OTHER_USER_ID, PRODUCT_ID);

            // act
            likeService.deleteAllByProduct(PRODUCT_ID);

            // assert
            Page<LikeEntity> userLikes = likeService.getLikedProducts(USER_ID, PageRequest.of(0, 20));
            Page<LikeEntity> otherUserLikes = likeService.getLikedProducts(OTHER_USER_ID, PageRequest.of(0, 20));
            assertAll(
                    () -> assertEquals(0, userLikes.getTotalElements()),
                    () -> assertEquals(0, otherUserLikes.getTotalElements())
            );
        }
    }

    @DisplayName("상품 연쇄 삭제 (복수)")
    @Nested
    class DeleteAllByProducts {

        @DisplayName("[State Transition] productIds에 해당하는 active 좋아요가 모두 soft delete된다.")
        @Test
        void softDeletesAllLikes_byProductIds() {
            // arrange
            likeService.like(USER_ID, PRODUCT_ID);
            likeService.like(USER_ID, OTHER_PRODUCT_ID);

            // act
            likeService.deleteAllByProducts(List.of(PRODUCT_ID, OTHER_PRODUCT_ID));

            // assert
            Page<LikeEntity> result = likeService.getLikedProducts(USER_ID, PageRequest.of(0, 20));
            assertEquals(0, result.getTotalElements());
        }
    }
}
