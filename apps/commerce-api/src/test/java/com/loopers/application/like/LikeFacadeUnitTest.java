package com.loopers.application.like;

import com.loopers.application.like.LikeService;
import com.loopers.application.product.ProductService;
import com.loopers.domain.product.Product;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class LikeFacadeUnitTest {

    @Mock private LikeService likeService;
    @Mock private ProductService productService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private LikeFacade likeFacade;

    @BeforeEach
    void setUp() {
        likeFacade = new LikeFacade(likeService, productService, eventPublisher);
    }

    private Product product(Long id) {
        return new Product(id, 1L, "청바지", BigDecimal.valueOf(50000), 0L,
            ZonedDateTime.now(), ZonedDateTime.now(), null);
    }

    @DisplayName("좋아요를 등록할 때,")
    @Nested
    class Like {

        @DisplayName("신규 좋아요이면, likeCount 증가 이벤트를 발행한다.")
        @Test
        void publishesLikeCountIncreasedEvent_whenNewLikeIsAdded() {
            given(productService.getProduct(10L)).willReturn(product(10L));
            given(likeService.like(1L, 10L)).willReturn(true);

            likeFacade.like(new LikeCommand.Like(1L, 10L));

            then(eventPublisher).should().publishEvent(new LikeCountChangedEvent(10L, true));
        }

        @DisplayName("이미 좋아요한 상품이면, 이벤트를 발행하지 않는다.")
        @Test
        void doesNotPublishEvent_whenAlreadyLiked() {
            given(productService.getProduct(10L)).willReturn(product(10L));
            given(likeService.like(1L, 10L)).willReturn(false);

            likeFacade.like(new LikeCommand.Like(1L, 10L));

            then(eventPublisher).should(never()).publishEvent(new LikeCountChangedEvent(10L, true));
        }

        @DisplayName("존재하지 않는 상품에 좋아요하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            given(productService.getProduct(9999L))
                .willThrow(new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));

            CoreException ex = assertThrows(CoreException.class,
                () -> likeFacade.like(new LikeCommand.Like(1L, 9999L)));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            then(likeService).should(never()).like(1L, 9999L);
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class Unlike {

        @DisplayName("좋아요를 취소하면, likeCount 감소 이벤트를 발행한다.")
        @Test
        void publishesLikeCountDecreasedEvent_whenLikeIsRemoved() {
            given(productService.getProduct(10L)).willReturn(product(10L));
            given(likeService.unlike(1L, 10L)).willReturn(true);

            likeFacade.unlike(new LikeCommand.Unlike(1L, 10L));

            then(eventPublisher).should().publishEvent(new LikeCountChangedEvent(10L, false));
        }

        @DisplayName("좋아요하지 않은 상품을 취소하면, 이벤트를 발행하지 않는다.")
        @Test
        void doesNotPublishEvent_whenNotLiked() {
            given(productService.getProduct(10L)).willReturn(product(10L));
            given(likeService.unlike(1L, 10L)).willReturn(false);

            likeFacade.unlike(new LikeCommand.Unlike(1L, 10L));

            then(eventPublisher).should(never()).publishEvent(new LikeCountChangedEvent(10L, false));
        }
    }

    @DisplayName("좋아요 목록을 조회할 때,")
    @Nested
    class GetLikedProducts {

        @DisplayName("타인의 좋아요 목록을 조회하면, FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenUserIdDoesNotMatch() {
            CoreException ex = assertThrows(CoreException.class,
                () -> likeFacade.getLikedProducts(new LikeCommand.GetLiked(1L, 999L)));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }
    }
}
