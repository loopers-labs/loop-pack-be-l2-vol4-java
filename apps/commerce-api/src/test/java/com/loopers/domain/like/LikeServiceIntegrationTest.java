package com.loopers.domain.like;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@Transactional
class LikeServiceIntegrationTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private UUID userId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        productId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요를 등록할 때,")
    @Nested
    class Like {

        @DisplayName("신규 좋아요이면, LikeModel이 저장되어 반환된다.")
        @Test
        void savesLike_whenNew() {
            // act
            LikeModel like = likeService.like(userId, productId);

            // assert
            assertAll(
                () -> assertThat(like.getId()).isNotNull(),
                () -> assertThat(like.getUserId()).isEqualTo(userId),
                () -> assertThat(like.getProductId()).isEqualTo(productId)
            );
        }

        @DisplayName("이미 좋아요한 경우, 기존 레코드를 반환한다 (멱등).")
        @Test
        void returnsExisting_whenAlreadyLiked() {
            // arrange
            LikeModel first = likeService.like(userId, productId);

            // act
            LikeModel second = likeService.like(userId, productId);

            // assert
            assertThat(second.getId()).isEqualTo(first.getId());
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class Unlike {

        @DisplayName("존재하는 좋아요이면, 삭제 후 true를 반환한다.")
        @Test
        void returnsTrue_whenLikeExists() {
            // arrange
            likeService.like(userId, productId);

            // act
            boolean result = likeService.unlike(userId, productId);

            // assert
            assertAll(
                () -> assertThat(result).isTrue(),
                () -> assertThat(likeService.find(userId, productId)).isEmpty()
            );
        }

        @DisplayName("존재하지 않는 좋아요이면, false를 반환한다 (멱등).")
        @Test
        void returnsFalse_whenLikeNotExists() {
            // act
            boolean result = likeService.unlike(userId, productId);

            // assert
            assertThat(result).isFalse();
        }
    }

    @DisplayName("좋아요를 단건 조회할 때,")
    @Nested
    class Find {

        @DisplayName("존재하는 경우, Optional에 LikeModel이 담겨 반환된다.")
        @Test
        void returnsLike_whenExists() {
            // arrange
            likeService.like(userId, productId);

            // act
            Optional<LikeModel> result = likeService.find(userId, productId);

            // assert
            assertAll(
                () -> assertThat(result).isPresent(),
                () -> assertThat(result.get().getUserId()).isEqualTo(userId),
                () -> assertThat(result.get().getProductId()).isEqualTo(productId)
            );
        }

        @DisplayName("존재하지 않는 경우, 빈 Optional을 반환한다.")
        @Test
        void returnsEmpty_whenNotExists() {
            // act
            Optional<LikeModel> result = likeService.find(userId, productId);

            // assert
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("유저의 좋아요 목록을 조회할 때,")
    @Nested
    class FindAllByUserId {

        @DisplayName("여러 상품에 좋아요한 경우, 페이징으로 조회된다.")
        @Test
        void returnsPagedLikes_whenMultipleLikes() {
            // arrange
            UUID productId2 = UUID.randomUUID();
            UUID productId3 = UUID.randomUUID();
            likeService.like(userId, productId);
            likeService.like(userId, productId2);
            likeService.like(userId, productId3);

            // act
            Page<LikeModel> page = likeService.findAllByUserId(userId, PageRequest.of(0, 2));

            // assert
            assertAll(
                () -> assertThat(page.getTotalElements()).isEqualTo(3),
                () -> assertThat(page.getContent()).hasSize(2)
            );
        }

        @DisplayName("다른 유저의 좋아요는 포함되지 않는다.")
        @Test
        void excludesOtherUsersLikes() {
            // arrange
            UUID otherUserId = UUID.randomUUID();
            likeService.like(userId, productId);
            likeService.like(otherUserId, productId);

            // act
            Page<LikeModel> page = likeService.findAllByUserId(userId, PageRequest.of(0, 10));

            // assert
            assertAll(
                () -> assertThat(page.getTotalElements()).isEqualTo(1),
                () -> assertThat(page.getContent().get(0).getUserId()).isEqualTo(userId)
            );
        }
    }
}
