package com.loopers.like.domain;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class LikeServiceIntegrationTest {

    private final LikeService likeService;
    private final LikeRepository likeRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    LikeServiceIntegrationTest(
        LikeService likeService,
        LikeRepository likeRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.likeService = likeService;
        this.likeRepository = likeRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요를 등록할 때 ")
    @Nested
    class LikeProduct {

        @DisplayName("사용자 ID와 상품 ID가 주어지면, 좋아요를 저장한다.")
        @Test
        void savesLike_whenUserIdAndProductIdAreProvided() {
            // arrange
            Long userId = 1L;
            Long productId = 101L;

            // act
            LikeChange change = likeService.like(userId, productId);

            // assert
            assertAll(
                () -> assertThat(change.productId()).isEqualTo(productId),
                () -> assertThat(change.countChangeAmount()).isEqualTo(1),
                () -> assertThat(change.hasCountChange()).isTrue(),
                () -> assertThat(likeRepository.findByUserIdAndProductId(userId, productId)).isPresent(),
                () -> assertThat(likeService.countProductLikes(productId)).isEqualTo(1)
            );
        }

        @DisplayName("이미 좋아요한 상품에 다시 등록하면, 변경 없이 기존 좋아요를 유지한다.")
        @Test
        void keepsOneLike_whenProductIsAlreadyLiked() {
            // arrange
            Long userId = 1L;
            Long productId = 101L;
            LikeChange created = likeService.like(userId, productId);

            // act
            LikeChange change = likeService.like(userId, productId);

            // assert
            assertAll(
                () -> assertThat(created.hasCountChange()).isTrue(),
                () -> assertThat(change.productId()).isEqualTo(productId),
                () -> assertThat(change.countChangeAmount()).isZero(),
                () -> assertThat(change.hasCountChange()).isFalse(),
                () -> assertThat(likeService.countProductLikes(productId)).isEqualTo(1)
            );
        }
    }

    @DisplayName("좋아요를 취소할 때 ")
    @Nested
    class UnlikeProduct {

        @DisplayName("기존 좋아요가 있으면, hard delete 한다.")
        @Test
        void deletesLike_whenLikeExists() {
            // arrange
            Long userId = 1L;
            Long productId = 101L;
            likeService.like(userId, productId);

            // act
            LikeChange change = likeService.unlike(userId, productId);

            // assert
            assertAll(
                () -> assertThat(change.productId()).isEqualTo(productId),
                () -> assertThat(change.countChangeAmount()).isEqualTo(-1),
                () -> assertThat(change.hasCountChange()).isTrue(),
                () -> assertThat(likeRepository.findByUserIdAndProductId(userId, productId)).isEmpty(),
                () -> assertThat(likeService.countProductLikes(productId)).isZero()
            );
        }

        @DisplayName("기존 좋아요가 없어도, 변경 없이 성공한다.")
        @Test
        void returnsWithoutChange_whenLikeDoesNotExist() {
            // arrange
            Long userId = 1L;
            Long productId = 101L;

            // act
            LikeChange change = likeService.unlike(userId, productId);

            // assert
            assertAll(
                () -> assertThat(change.productId()).isEqualTo(productId),
                () -> assertThat(change.countChangeAmount()).isZero(),
                () -> assertThat(change.hasCountChange()).isFalse(),
                () -> assertThat(likeService.countProductLikes(productId)).isZero()
            );
        }
    }

    @DisplayName("좋아요 저장 제약을 검증할 때 ")
    @Nested
    class SaveLike {

        @DisplayName("같은 사용자와 상품 조합을 중복 저장하면, DB unique 제약으로 실패한다.")
        @Test
        void throwsDataIntegrityViolation_whenSameUserAndProductAreSavedTwice() {
            // arrange
            Long userId = 1L;
            Long productId = 101L;
            likeRepository.save(Like.create(userId, productId));

            // act & assert
            assertThatThrownBy(() -> likeRepository.save(Like.create(userId, productId)))
                .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
