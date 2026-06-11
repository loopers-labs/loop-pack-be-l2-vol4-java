package com.loopers.like.infrastructure;

import com.loopers.like.domain.Like;
import com.loopers.like.domain.LikeRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LikeRepositoryIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long OTHER_PRODUCT_ID = 20L;

    private final LikeRepository likeRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public LikeRepositoryIntegrationTest(LikeRepository likeRepository, DatabaseCleanUp databaseCleanUp) {
        this.likeRepository = likeRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("save 후 findByUserIdAndProductId 로 같은 like 를 조회할 수 있다")
    void givenSavedLike_whenFindByUserIdAndProductId_thenReturnsLike() {
        Like saved = likeRepository.save(Like.create(USER_ID, PRODUCT_ID));

        Optional<Like> found = likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("존재하지 않는 조합으로 findByUserIdAndProductId 하면 빈 값을 반환한다")
    void givenNonExistingCombination_whenFindByUserIdAndProductId_thenReturnsEmpty() {
        Optional<Like> found = likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("soft-delete 된 like 도 findByUserIdAndProductId 로 조회할 수 있다 (재등록 용)")
    void givenSoftDeletedLike_whenFindByUserIdAndProductId_thenStillReturnsLike() {
        Like saved = likeRepository.save(Like.create(USER_ID, PRODUCT_ID));
        saved.delete();
        likeRepository.save(saved);

        Optional<Like> found = likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID);

        assertThat(found).isPresent();
        assertThat(found.get().getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("findActiveByUserId 는 활성 좋아요만 반환한다 (soft-delete 제외)")
    void givenActiveAndDeletedLikes_whenFindActiveByUserId_thenReturnsOnlyActive() {
        likeRepository.save(Like.create(USER_ID, PRODUCT_ID));
        Like deleted = likeRepository.save(Like.create(USER_ID, OTHER_PRODUCT_ID));
        deleted.delete();
        likeRepository.save(deleted);

        List<Like> result = likeRepository.findActiveByUserId(USER_ID);

        assertThat(result)
                .hasSize(1)
                .extracting(Like::getProductId)
                .containsExactly(PRODUCT_ID);
    }

    @Test
    @DisplayName("findActiveByUserId 는 다른 사용자의 좋아요는 반환하지 않는다")
    void givenLikesFromDifferentUsers_whenFindActiveByUserId_thenReturnsOnlyOwnerLikes() {
        likeRepository.save(Like.create(USER_ID, PRODUCT_ID));
        likeRepository.save(Like.create(OTHER_USER_ID, PRODUCT_ID));

        List<Like> result = likeRepository.findActiveByUserId(USER_ID);

        assertThat(result)
                .hasSize(1)
                .extracting(Like::getUserId)
                .containsExactly(USER_ID);
    }

    @Test
    @DisplayName("findActiveByUserId 는 좋아요가 없으면 빈 리스트를 반환한다")
    void givenNoLikes_whenFindActiveByUserId_thenReturnsEmptyList() {
        List<Like> result = likeRepository.findActiveByUserId(USER_ID);

        assertThat(result).isEmpty();
    }
}
