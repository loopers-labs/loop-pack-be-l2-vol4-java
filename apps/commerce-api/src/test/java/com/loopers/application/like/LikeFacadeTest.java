package com.loopers.application.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.like.event.LikeAdded;
import com.loopers.domain.like.event.LikeRemoved;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LikeFacadeTest {

    private static final String LOGIN_ID = "tester01";
    private static final Long PRODUCT_ID = 100L;

    private final LikeRepository likeRepository = mock(LikeRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final LikeFacade likeFacade =
        new LikeFacade(likeRepository, productRepository, userRepository, eventPublisher);

    private void givenUser(long id) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(userRepository.findByLoginId(LOGIN_ID)).thenReturn(Optional.of(user));
    }

    private Product product() {
        return new Product(1L, "에어맥스", "운동화", 1000L, 10);
    }

    @DisplayName("좋아요를 등록할 때, ")
    @Nested
    class Liking {

        @DisplayName("아직 좋아요하지 않았으면, Like 저장 후 LikeAdded 이벤트를 발행한다.")
        @Test
        void savesAndPublishes() {
            givenUser(7L);
            when(likeRepository.existsBy(7L, PRODUCT_ID)).thenReturn(false);
            when(productRepository.find(PRODUCT_ID)).thenReturn(Optional.of(product()));

            likeFacade.like(LOGIN_ID, PRODUCT_ID);

            verify(likeRepository).save(any(Like.class));
            ArgumentCaptor<LikeAdded> captor = ArgumentCaptor.forClass(LikeAdded.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().userId()).isEqualTo(7L);
            assertThat(captor.getValue().productId()).isEqualTo(PRODUCT_ID);
        }

        @DisplayName("이미 좋아요한 경우, 저장/발행 모두 하지 않는다. (멱등)")
        @Test
        void idempotent() {
            givenUser(7L);
            when(likeRepository.existsBy(7L, PRODUCT_ID)).thenReturn(true);

            likeFacade.like(LOGIN_ID, PRODUCT_ID);

            verify(likeRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @DisplayName("상품이 없으면 NOT_FOUND 이고 저장·발행도 하지 않는다.")
        @Test
        void throwsNotFound_whenProductMissing() {
            givenUser(7L);
            when(likeRepository.existsBy(7L, PRODUCT_ID)).thenReturn(false);
            when(productRepository.find(PRODUCT_ID)).thenReturn(Optional.empty());

            CoreException ex = assertThrows(CoreException.class, () -> likeFacade.like(LOGIN_ID, PRODUCT_ID));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(likeRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @DisplayName("좋아요를 취소할 때, ")
    @Nested
    class Unlike {

        @DisplayName("좋아요한 상태면, Like 삭제 후 LikeRemoved 이벤트를 발행한다.")
        @Test
        void deletesAndPublishes() {
            givenUser(7L);
            when(likeRepository.existsBy(7L, PRODUCT_ID)).thenReturn(true);

            likeFacade.unlike(LOGIN_ID, PRODUCT_ID);

            verify(likeRepository).deleteBy(7L, PRODUCT_ID);
            verify(eventPublisher).publishEvent(any(LikeRemoved.class));
        }

        @DisplayName("좋아요하지 않은 상태면, 삭제/발행 모두 하지 않는다. (멱등)")
        @Test
        void idempotent() {
            givenUser(7L);
            when(likeRepository.existsBy(7L, PRODUCT_ID)).thenReturn(false);

            likeFacade.unlike(LOGIN_ID, PRODUCT_ID);

            verify(likeRepository, never()).deleteBy(any(), any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }
}
