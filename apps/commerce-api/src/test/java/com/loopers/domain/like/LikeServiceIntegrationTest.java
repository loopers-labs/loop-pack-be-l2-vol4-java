package com.loopers.domain.like;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 좋아요 등록/취소의 영속 정합성(좋아요 "사실")과 카운터 이벤트 발행을 검증한다.
 *
 * <p><b>카운터(product.likes_count)는 비동기로 분리됐다</b>(hot row 회피). 좋아요 트랜잭션은
 * product_like 행만 강하게 커밋하고 카운터는 {@link LikeChangedEvent}로 발행 → commerce-streamer가
 * 집계한다. 따라서 여기서는 likesCount 즉시값을 검증하지 않고, (1) 좋아요 행 정합성과 (2) 전이가
 * 실제로 일어났을 때만 이벤트가 발행되는지를 검증한다. 좋아요→카운트 반영의 end-to-end는 streamer 테스트가 맡는다.
 */
@SpringBootTest
@RecordApplicationEvents
public class LikeServiceIntegrationTest {

    @Autowired LikeService likeService;
    @Autowired ProductService productService;
    @Autowired LikeRepository likeRepository;
    @Autowired DatabaseCleanUp databaseCleanUp;
    @Autowired ApplicationEvents events;

    private static final Long USER_ID = 100L;
    private Long productId;

    @BeforeEach
    void setUp() {
        ProductModel product = productService.createProduct(1L, "에어맥스", "러닝화", null, 139000L);
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private List<LikeChangedEvent> likeEvents() {
        return events.stream(LikeChangedEvent.class).toList();
    }

    @Nested
    @DisplayName("좋아요를 등록할 때")
    class Like {

        @DisplayName("활성 상품에 좋아요하면, 저장되고 +1 이벤트가 발행된다.")
        @Test
        void given_activeProduct_when_like_then_savedAndEventPublished() {
            likeService.like(USER_ID, productId);

            assertAll(
                    () -> assertThat(likeService.isLiked(USER_ID, productId)).isTrue(),
                    () -> assertThat(likeEvents()).containsExactly(LikeChangedEvent.liked(productId))
            );
        }

        @DisplayName("같은 좋아요를 두 번 해도 멱등하게 처리되어 이벤트는 1건만 발행된다.")
        @Test
        void given_alreadyLiked_when_likeAgain_then_idempotent() {
            likeService.like(USER_ID, productId);
            likeService.like(USER_ID, productId);

            assertAll(
                    () -> assertThat(likeService.isLiked(USER_ID, productId)).isTrue(),
                    () -> assertThat(likeEvents()).containsExactly(LikeChangedEvent.liked(productId))
            );
        }

        @DisplayName("존재하지 않는 상품에 좋아요하면, NotFound 예외가 발생하고 이벤트는 없다.")
        @Test
        void given_nonExistingProduct_when_like_then_throwsNotFound() {
            Throwable thrown = catchThrowable(() -> likeService.like(USER_ID, 9999L));

            assertAll(
                    () -> assertThat(thrown).isInstanceOf(CoreException.class),
                    () -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND),
                    () -> assertThat(likeEvents()).isEmpty()
            );
        }
    }

    @Nested
    @DisplayName("좋아요를 취소할 때")
    class Unlike {

        @DisplayName("좋아요를 취소하면, 비활성되고 -1 이벤트가 발행된다.")
        @Test
        void given_liked_when_unlike_then_inactiveAndEventPublished() {
            likeService.like(USER_ID, productId);

            likeService.unlike(USER_ID, productId);

            assertAll(
                    () -> assertThat(likeService.isLiked(USER_ID, productId)).isFalse(),
                    () -> assertThat(likeEvents()).containsExactly(
                            LikeChangedEvent.liked(productId), LikeChangedEvent.unliked(productId))
            );
        }

        @DisplayName("좋아요하지 않은 상태에서 취소해도 멱등하게 처리되어 이벤트는 없다.")
        @Test
        void given_notLiked_when_unlike_then_idempotent() {
            likeService.unlike(USER_ID, productId);

            assertAll(
                    () -> assertThat(likeService.isLiked(USER_ID, productId)).isFalse(),
                    () -> assertThat(likeEvents()).isEmpty()
            );
        }
    }

    @Nested
    @DisplayName("좋아요를 재등록할 때")
    class Reactivate {

        @DisplayName("좋아요→취소→재등록하면, 새 행이 아니라 같은 행이 reactivate되고 이벤트는 +1,-1,+1 순으로 발행된다.")
        @Test
        void given_unliked_when_likeAgain_then_reactivatesSameRow() {
            likeService.like(USER_ID, productId);
            Long firstId = likeRepository.findByUserIdAndProductId(USER_ID, productId).orElseThrow().getId();
            likeService.unlike(USER_ID, productId);

            likeService.like(USER_ID, productId);

            Long secondId = likeRepository.findByUserIdAndProductId(USER_ID, productId).orElseThrow().getId();
            assertAll(
                    () -> assertThat(secondId).isEqualTo(firstId),   // 같은 행 (새 INSERT 아님)
                    () -> assertThat(likeService.isLiked(USER_ID, productId)).isTrue(),
                    () -> assertThat(likeEvents()).containsExactly(
                            LikeChangedEvent.liked(productId),
                            LikeChangedEvent.unliked(productId),
                            LikeChangedEvent.liked(productId))
            );
        }
    }
}
