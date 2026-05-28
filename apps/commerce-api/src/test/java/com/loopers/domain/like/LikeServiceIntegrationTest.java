package com.loopers.domain.like;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LikeServiceIntegrationTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요 등록할 때")
    @Nested
    class Like {

        @DisplayName("복합키에 해당하는 데이터가 없으면, 행이 새로 생성되고 좋아요 상태가 된다.")
        @Test
        void createsRow_whenFirst() {
            // when
            likeService.like(1L, 2L);

            // then
            LikeModel found = likeRepository.find(LikeId.of(1L, 2L)).orElseThrow();
            assertThat(found.isLiked()).isTrue();
        }

        @DisplayName("복합키에 해당하는 데이터가 있고 취소 상태였으면, 다시 좋아요 상태가 된다.")
        @Test
        void resetsLikedAt_whenReLiked() {
            // given
            likeService.like(1L, 2L);
            likeService.unlike(1L, 2L);

            // when
            likeService.like(1L, 2L);

            // then
            LikeModel found = likeRepository.find(LikeId.of(1L, 2L)).orElseThrow();
            assertThat(found.isLiked()).isTrue();
        }

        @DisplayName("복합키에 해당하는 데이터가 있고 이미 좋아요 상태면, likedAt 시각이 유지된다.")
        @Test
        void keepsLikedAt_whenIdempotent() throws InterruptedException {
            // given
            likeService.like(1L, 2L);
            ZonedDateTime before = likeRepository.find(LikeId.of(1L, 2L)).orElseThrow().getLikedAt();
            Thread.sleep(10);

            // when
            likeService.like(1L, 2L);

            // then
            ZonedDateTime after = likeRepository.find(LikeId.of(1L, 2L)).orElseThrow().getLikedAt();
            assertThat(after).isEqualTo(before);
        }
    }

    @DisplayName("좋아요 취소할 때")
    @Nested
    class Unlike {

        @DisplayName("복합키에 해당하는 데이터가 있고 좋아요 상태였으면, 취소 상태(likedAt = null)가 된다.")
        @Test
        void clears_whenLiked() {
            // given
            likeService.like(1L, 2L);

            // when
            likeService.unlike(1L, 2L);

            // then
            LikeModel found = likeRepository.find(LikeId.of(1L, 2L)).orElseThrow();
            assertThat(found.isLiked()).isFalse();
        }

        @DisplayName("복합키에 해당하는 데이터가 있고 이미 취소 상태에서 다시 호출해도, 취소 상태가 유지된다.")
        @Test
        void keepsCancelled_whenIdempotent() {
            // given
            likeService.like(1L, 2L);
            likeService.unlike(1L, 2L);

            // when
            likeService.unlike(1L, 2L);

            // then
            LikeModel found = likeRepository.find(LikeId.of(1L, 2L)).orElseThrow();
            assertThat(found.isLiked()).isFalse();
        }

        @DisplayName("복합키에 해당하는 데이터가 없으면, 아무것도 하지 않는다.")
        @Test
        void doesNothing_whenNoRow() {
            // when
            likeService.unlike(1L, 2L);

            // then
            assertThat(likeRepository.find(LikeId.of(1L, 2L))).isEmpty();
        }
    }
}