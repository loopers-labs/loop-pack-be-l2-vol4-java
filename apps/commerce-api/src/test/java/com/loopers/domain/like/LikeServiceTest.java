package com.loopers.domain.like;

import com.loopers.domain.product.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LikeService 순수 단위 테스트 — Repository/ProductService를 mock으로 격리해 DB 없이
 * 좋아요 등록/취소 흐름의 분기(신규/멱등/재활성)와 카운터 이벤트 발행 여부를 검증한다.
 *
 * <p>카운터 증감은 동기 UPDATE가 아니라 {@link LikeChangedEvent} 발행으로 분리됐다(hot row 회피).
 * 따라서 "실제로 좋아요 전이가 일어났을 때만 이벤트를 1건 발행"하는지를 검증한다.
 * (실제 영속·집계 정합성은 LikeServiceIntegrationTest / streamer 테스트가 검증)
 */
class LikeServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;

    private LikeRepository likeRepository;
    private ProductService productService;
    private ApplicationEventPublisher eventPublisher;
    private LikeService likeService;

    @BeforeEach
    void setUp() {
        likeRepository = mock(LikeRepository.class);
        productService = mock(ProductService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        likeService = new LikeService(likeRepository, productService, eventPublisher);
    }

    @Nested
    @DisplayName("좋아요 등록")
    class Like {

        @DisplayName("좋아요한 적 없으면, 저장하고 +1 이벤트를 발행한다.")
        @Test
        void given_noLike_when_like_then_savesAndPublishesIncrement() {
            when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.empty());

            likeService.like(USER_ID, PRODUCT_ID);

            verify(likeRepository).save(any(LikeModel.class));
            verify(eventPublisher).publishEvent(LikeChangedEvent.liked(PRODUCT_ID));
        }

        @DisplayName("이미 좋아요 상태면, 멱등하게 저장·이벤트 발행을 하지 않는다.")
        @Test
        void given_alreadyLiked_when_like_then_idempotentNoOp() {
            LikeModel active = new LikeModel(USER_ID, PRODUCT_ID);
            when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.of(active));

            likeService.like(USER_ID, PRODUCT_ID);

            verify(likeRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @DisplayName("취소된 좋아요가 있으면, 원자적으로 활성화하고 +1 이벤트를 발행한다.")
        @Test
        void given_canceledLike_when_like_then_activatesAndPublishesIncrement() {
            LikeModel canceled = new LikeModel(USER_ID, PRODUCT_ID);
            canceled.delete();
            when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.of(canceled));
            when(likeRepository.activate(USER_ID, PRODUCT_ID)).thenReturn(1);   // 이 트랜잭션이 실제 전이

            likeService.like(USER_ID, PRODUCT_ID);

            verify(likeRepository).activate(USER_ID, PRODUCT_ID);
            verify(eventPublisher).publishEvent(LikeChangedEvent.liked(PRODUCT_ID));
        }

        @DisplayName("취소된 좋아요를 동시에 재활성하면, 실제 전이하지 못한 쪽(영향 행 0)은 이벤트를 발행하지 않는다.")
        @Test
        void given_canceledLike_when_activateAffectsNoRow_then_noEvent() {
            LikeModel canceled = new LikeModel(USER_ID, PRODUCT_ID);
            canceled.delete();
            when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.of(canceled));
            when(likeRepository.activate(USER_ID, PRODUCT_ID)).thenReturn(0);   // 다른 트랜잭션이 먼저 전이

            likeService.like(USER_ID, PRODUCT_ID);

            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("좋아요 취소")
    class Unlike {

        @DisplayName("활성 좋아요가 있으면, 원자적으로 비활성화하고 -1 이벤트를 발행한다.")
        @Test
        void given_activeLike_when_unlike_then_deactivatesAndPublishesDecrement() {
            LikeModel active = new LikeModel(USER_ID, PRODUCT_ID);
            when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.of(active));
            when(likeRepository.deactivate(USER_ID, PRODUCT_ID)).thenReturn(1);

            likeService.unlike(USER_ID, PRODUCT_ID);

            verify(likeRepository).deactivate(USER_ID, PRODUCT_ID);
            verify(eventPublisher).publishEvent(LikeChangedEvent.unliked(PRODUCT_ID));
        }

        @DisplayName("활성 좋아요를 동시에 취소하면, 실제 전이하지 못한 쪽(영향 행 0)은 이벤트를 발행하지 않는다.")
        @Test
        void given_activeLike_when_deactivateAffectsNoRow_then_noEvent() {
            LikeModel active = new LikeModel(USER_ID, PRODUCT_ID);
            when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.of(active));
            when(likeRepository.deactivate(USER_ID, PRODUCT_ID)).thenReturn(0);

            likeService.unlike(USER_ID, PRODUCT_ID);

            verify(eventPublisher, never()).publishEvent(any());
        }

        @DisplayName("좋아요가 없으면, 멱등하게 저장·이벤트 발행을 하지 않는다.")
        @Test
        void given_noLike_when_unlike_then_idempotentNoOp() {
            when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.empty());

            likeService.unlike(USER_ID, PRODUCT_ID);

            verify(likeRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @DisplayName("이미 취소된 좋아요면, 멱등하게 저장·이벤트 발행을 하지 않는다.")
        @Test
        void given_alreadyCanceled_when_unlike_then_idempotentNoOp() {
            LikeModel canceled = new LikeModel(USER_ID, PRODUCT_ID);
            canceled.delete();
            when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.of(canceled));

            likeService.unlike(USER_ID, PRODUCT_ID);

            verify(likeRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }
}
