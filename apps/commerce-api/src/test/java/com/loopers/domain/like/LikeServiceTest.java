package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long OTHER_PRODUCT_ID = 20L;

    @Mock
    private LikeRepository likeRepository;

    @InjectMocks
    private LikeService likeService;

    private LikeEntity activeLike(Long id, Long userId, Long productId) {
        return LikeEntity.of(id, userId, productId, ZonedDateTime.now(), ZonedDateTime.now(), null);
    }

    private LikeEntity deletedLike(Long id, Long userId, Long productId) {
        return LikeEntity.of(id, userId, productId, ZonedDateTime.now(), ZonedDateTime.now(), ZonedDateTime.now());
    }

    @DisplayName("좋아요 등록")
    @Nested
    class Like {

        @DisplayName("[ECP] 존재하지 않는 좋아요를 등록하면 새 LikeEntity가 저장된다.")
        @Test
        void savesNewLike_whenNotExists() {
            // arrange
            LikeEntity saved = activeLike(1L, USER_ID, PRODUCT_ID);
            given(likeRepository.findAny(USER_ID, PRODUCT_ID)).willReturn(Optional.empty());
            given(likeRepository.save(any())).willReturn(saved);

            // act
            LikeEntity result = likeService.like(USER_ID, PRODUCT_ID);

            // assert
            assertAll(
                    () -> assertNotNull(result.getId()),
                    () -> assertEquals(USER_ID, result.getUserId()),
                    () -> assertEquals(PRODUCT_ID, result.getProductId()),
                    () -> assertNull(result.getDeletedAt())
            );
            verify(likeRepository).findAny(USER_ID, PRODUCT_ID);
            verify(likeRepository).save(any());
        }

        @DisplayName("[ECP] 이미 active 좋아요가 존재하면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenAlreadyLiked() {
            // arrange
            given(likeRepository.findAny(USER_ID, PRODUCT_ID))
                    .willReturn(Optional.of(activeLike(1L, USER_ID, PRODUCT_ID)));

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> likeService.like(USER_ID, PRODUCT_ID));
            assertEquals(ErrorType.CONFLICT, exception.getErrorType());
        }

        @DisplayName("[State Transition] soft-deleted 좋아요가 존재하면 restore되어 deletedAt이 null이 된다.")
        @Test
        void restoresLike_whenSoftDeletedExists() {
            // arrange
            LikeEntity softDeleted = deletedLike(1L, USER_ID, PRODUCT_ID);
            given(likeRepository.findAny(USER_ID, PRODUCT_ID)).willReturn(Optional.of(softDeleted));
            given(likeRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            // act
            LikeEntity result = likeService.like(USER_ID, PRODUCT_ID);

            // assert
            assertNull(result.getDeletedAt());
            verify(likeRepository).save(softDeleted);
        }
    }

    @DisplayName("좋아요 취소")
    @Nested
    class Unlike {

        @DisplayName("[State Transition] active 좋아요를 취소하면 엔티티가 soft delete 상태로 저장된다.")
        @Test
        void softDeletesLike_whenActive() {
            // arrange
            LikeEntity active = activeLike(1L, USER_ID, PRODUCT_ID);
            given(likeRepository.findActive(USER_ID, PRODUCT_ID)).willReturn(Optional.of(active));
            given(likeRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            // act
            likeService.unlike(USER_ID, PRODUCT_ID);

            // assert
            assertTrue(active.isDeleted());
            verify(likeRepository).save(active);
        }

        @DisplayName("[ECP] 존재하지 않는 좋아요를 취소하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotExists() {
            // arrange
            given(likeRepository.findActive(USER_ID, PRODUCT_ID)).willReturn(Optional.empty());

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
            List<LikeEntity> likes = List.of(
                    activeLike(1L, USER_ID, PRODUCT_ID),
                    activeLike(2L, USER_ID, OTHER_PRODUCT_ID)
            );
            PageRequest pageable = PageRequest.of(0, 20);
            given(likeRepository.findActiveByUserId(USER_ID, pageable))
                    .willReturn(new PageImpl<>(likes, pageable, 2));

            // act
            Page<LikeEntity> result = likeService.getLikedProducts(USER_ID, pageable);

            // assert
            assertAll(
                    () -> assertEquals(2, result.getTotalElements()),
                    () -> assertTrue(result.getContent().stream()
                            .allMatch(like -> like.getUserId().equals(USER_ID)))
            );
            verify(likeRepository).findActiveByUserId(USER_ID, pageable);
        }
    }

    @DisplayName("상품 연쇄 삭제 (단건)")
    @Nested
    class DeleteAllByProduct {

        @DisplayName("[State Transition] productId에 해당하는 좋아요가 모두 soft delete된다.")
        @Test
        void softDeletesAllLikes_byProductId() {
            // act
            likeService.deleteAllByProduct(PRODUCT_ID);

            // assert
            verify(likeRepository).deleteAllByProductId(PRODUCT_ID);
        }
    }

    @DisplayName("상품 연쇄 삭제 (복수)")
    @Nested
    class DeleteAllByProducts {

        @DisplayName("[State Transition] productIds에 해당하는 좋아요가 모두 soft delete된다.")
        @Test
        void softDeletesAllLikes_byProductIds() {
            // arrange
            List<Long> productIds = List.of(PRODUCT_ID, OTHER_PRODUCT_ID);

            // act
            likeService.deleteAllByProducts(productIds);

            // assert
            verify(likeRepository).deleteAllByProductIds(productIds);
        }
    }
}
