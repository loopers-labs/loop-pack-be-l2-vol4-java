package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@Transactional
abstract class LikeRepositoryContractTest {

    abstract LikeRepository repository();

    @DisplayName("INSERT IGNORE 로 좋아요를 저장할 때, 동일한 (userId, productId) 가 없으면 true 를 반환하고 DB 에 저장된다.")
    @Test
    void save_returnsTrue_whenLikeDoesNotExist() {
        // given
        Long userId = 1L;
        Long productId = 1L;

        // when
        boolean result = repository().save(new LikeModel(userId, productId));

        // then
        assertAll(
                () -> assertThat(result).isTrue(),
                () -> assertThat(repository().findByUserIdAndProductId(userId, productId)).isPresent()
        );
    }

    @DisplayName("INSERT IGNORE 로 좋아요를 저장할 때, 동일한 (userId, productId) 가 이미 존재하면 false 를 반환하고 중복 삽입되지 않는다.")
    @Test
    void save_returnsFalse_whenLikeAlreadyExists() {
        // given
        Long userId = 2L;
        Long productId = 2L;
        repository().save(new LikeModel(userId, productId));

        // when
        boolean result = repository().save(new LikeModel(userId, productId));

        // then
        assertAll(
                () -> assertThat(result).isFalse(),
                () -> assertThat(repository().findAllByUserId(userId)).hasSize(1)
        );
    }

    @DisplayName("원자적 DELETE 로 좋아요를 삭제할 때, 존재하는 좋아요를 삭제하면 true 를 반환하고 DB 에서 제거된다.")
    @Test
    void deleteByUserIdAndProductId_returnsTrue_whenLikeExists() {
        // given
        Long userId = 3L;
        Long productId = 3L;
        repository().save(new LikeModel(userId, productId));

        // when
        boolean result = repository().deleteByUserIdAndProductId(userId, productId);

        // then
        assertAll(
                () -> assertThat(result).isTrue(),
                () -> assertThat(repository().findByUserIdAndProductId(userId, productId)).isEmpty()
        );
    }

    @DisplayName("원자적 DELETE 로 좋아요를 삭제할 때, 존재하지 않는 좋아요를 삭제하면 false 를 반환한다.")
    @Test
    void deleteByUserIdAndProductId_returnsFalse_whenLikeDoesNotExist() {
        // given
        Long userId = 4L;
        Long productId = 4L;

        // when
        boolean result = repository().deleteByUserIdAndProductId(userId, productId);

        // then
        assertThat(result).isFalse();
    }
}
