package com.loopers.domain.like;

import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class LikeServiceIntegrationTest {
    @Autowired
    private LikeService likeService;

    @Autowired
    private LikeJpaRepository likeJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요를 등록할 때, ")
    @Nested
    class Register {
        @DisplayName("처음 좋아요를 등록하면, product_like 행이 1개 생성된다.")
        @Test
        void createsLikeRow_whenFirstTime() {
            // arrange
            Long userId = 1L;
            Long productId = 10L;

            // act
            likeService.like(userId, productId);

            // assert
            List<Like> all = likeJpaRepository.findAll();
            assertAll(
                () -> assertThat(all).hasSize(1),
                () -> assertThat(all.get(0).getUserId()).isEqualTo(userId),
                () -> assertThat(all.get(0).getProductId()).isEqualTo(productId)
            );
        }

        @DisplayName("이미 좋아요가 있는 상태에서 다시 등록해도, 행은 1개 유지된다.")
        @Test
        void keepsSingleRow_whenLikeAlreadyExists() {
            // arrange
            Long userId = 1L;
            Long productId = 10L;
            likeJpaRepository.save(new Like(userId, productId));

            // act
            likeService.like(userId, productId);

            // assert
            assertThat(likeJpaRepository.findAll()).hasSize(1);
        }

        @DisplayName("소프트 삭제된 좋아요를 다시 등록하면, 같은 행이 복원된다.")
        @Test
        void restoresLike_whenPreviouslySoftDeleted() {
            // arrange
            Long userId = 1L;
            Long productId = 10L;
            Like deleted = new Like(userId, productId);
            deleted.delete();
            Like saved = likeJpaRepository.save(deleted);

            // act
            likeService.like(userId, productId);

            // assert
            List<Like> all = likeJpaRepository.findAll();
            assertAll(
                () -> assertThat(all).hasSize(1),
                () -> assertThat(all.get(0).getId()).isEqualTo(saved.getId()),
                () -> assertThat(all.get(0).getDeletedAt()).isNull()
            );
        }
    }

    @DisplayName("좋아요를 취소할 때, ")
    @Nested
    class Cancel {
        @DisplayName("활성 좋아요를 취소하면, 같은 행이 소프트 삭제된다.")
        @Test
        void softDeletesLike_whenActive() {
            // arrange
            Long userId = 1L;
            Long productId = 10L;
            Like saved = likeJpaRepository.save(new Like(userId, productId));

            // act
            likeService.unlike(userId, productId);

            // assert
            List<Like> all = likeJpaRepository.findAll();
            assertAll(
                () -> assertThat(all).hasSize(1),
                () -> assertThat(all.get(0).getId()).isEqualTo(saved.getId()),
                () -> assertThat(all.get(0).getDeletedAt()).isNotNull()
            );
        }
    }
}
