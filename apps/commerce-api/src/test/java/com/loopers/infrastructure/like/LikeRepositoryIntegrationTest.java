package com.loopers.infrastructure.like;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class LikeRepositoryIntegrationTest {

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private LikeJpaRepository likeJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private LikeModel createLike(Long userId, Long productId) {
        return LikeModel.of(userId, productId);
    }

    @DisplayName("좋아요를 저장할 때,")
    @Nested
    class Save {

        @DisplayName("저장하면 식별자가 부여되고 userId·productId가 보존된다.")
        @Test
        void assignsId_andPreservesFields() {
            // arrange & act
            LikeModel savedLike = likeRepository.save(createLike(1L, 1L));

            // assert
            LikeModel reloadedLike = likeJpaRepository.findById(savedLike.getId()).orElseThrow();
            assertAll(
                () -> assertThat(savedLike.getId()).isNotNull(),
                () -> assertThat(reloadedLike.getUserId()).isEqualTo(1L),
                () -> assertThat(reloadedLike.getProductId()).isEqualTo(1L)
            );
        }
    }

    @DisplayName("(userId, productId) 조합 존재 여부를 조회할 때,")
    @Nested
    class ExistsByUserIdAndProductId {

        @DisplayName("저장된 조합이면 true, 없으면 false를 반환한다.")
        @Test
        void returnsTrueForExisting_andFalseOtherwise() {
            // arrange
            likeRepository.save(createLike(1L, 1L));

            // act & assert
            assertAll(
                () -> assertThat(likeRepository.existsByUserIdAndProductId(1L, 1L)).isTrue(),
                () -> assertThat(likeRepository.existsByUserIdAndProductId(1L, 2L)).isFalse(),
                () -> assertThat(likeRepository.existsByUserIdAndProductId(2L, 1L)).isFalse()
            );
        }
    }

    @DisplayName("동일한 (userId, productId)로 중복 저장을 시도할 때,")
    @Nested
    class UniqueConstraint {

        @DisplayName("동일한 (userId, productId) 조합을 다시 저장하면 저장이 거부된다.")
        @Test
        void throwsDataIntegrityViolation_whenDuplicateSave() {
            // arrange
            likeRepository.save(createLike(1L, 1L));

            // act & assert
            assertThatThrownBy(() -> likeRepository.save(createLike(1L, 1L)))
                .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
