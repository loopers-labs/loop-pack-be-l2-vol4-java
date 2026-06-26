package com.loopers.like.application;

import com.loopers.like.domain.Like;
import com.loopers.like.domain.LikeErrorCode;
import com.loopers.like.domain.LikeRepository;
import com.loopers.product.application.ProductReader;
import com.loopers.product.application.event.ProductLikeChangedEvent;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LikeServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;

    private final LikeRepository likeRepository = mock(LikeRepository.class);
    private final ProductReader productReader = mock(ProductReader.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final LikeService likeService = new LikeService(likeRepository, productReader, eventPublisher);

    @Test
    @DisplayName("register: 상품 존재 + Like 없으면 새로 저장한다")
    void givenActiveProductAndNoLike_whenRegister_thenSavesNewLike() {
        when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.empty());

        likeService.register(USER_ID, PRODUCT_ID);

        ArgumentCaptor<Like> captor = ArgumentCaptor.forClass(Like.class);
        verify(likeRepository).save(captor.capture());
        assertAll(
                () -> assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID),
                () -> assertThat(captor.getValue().getProductId()).isEqualTo(PRODUCT_ID)
        );
        verify(eventPublisher).publishEvent(new ProductLikeChangedEvent(PRODUCT_ID, 1L));
    }

    @Test
    @DisplayName("register: 이미 active Like 가 있으면 restore (deletedAt 그대로 null)")
    void givenActiveLike_whenRegister_thenRemainsActive() {
        Like active = Like.create(USER_ID, PRODUCT_ID);
        when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.of(active));

        likeService.register(USER_ID, PRODUCT_ID);

        assertThat(active.getDeletedAt()).isNull();
        verify(likeRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("register: 취소된 Like 가 있으면 restore (deletedAt 클리어)")
    void givenCancelledLike_whenRegister_thenRestoresLike() {
        Like cancelled = Like.create(USER_ID, PRODUCT_ID);
        cancelled.delete();
        when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.of(cancelled));

        likeService.register(USER_ID, PRODUCT_ID);

        assertThat(cancelled.getDeletedAt()).isNull();
        verify(likeRepository, never()).save(any());
        verify(eventPublisher).publishEvent(new ProductLikeChangedEvent(PRODUCT_ID, 1L));
    }

    @Test
    @DisplayName("register: 삭제·판매중지 상품(ensureActiveExists 실패)이면 NOT_FOUND 가 전파되고 Like 저장하지 않는다")
    void givenNonActiveProduct_whenRegister_thenPropagatesNotFoundAndSavesNothing() {
        doThrow(new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."))
                .when(productReader).ensureActiveExists(PRODUCT_ID);

        assertThatThrownBy(() -> likeService.register(USER_ID, PRODUCT_ID))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorType.NOT_FOUND);

        verify(likeRepository, never()).save(any());
    }

    @Test
    @DisplayName("register: 동시 요청으로 unique 제약을 위반하면 CONFLICT 로 변환한다")
    void givenUniqueViolation_whenRegister_thenThrowsConflict() {
        when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.empty());
        when(likeRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("uk_likes_user_id_product_id"));

        assertThatThrownBy(() -> likeService.register(USER_ID, PRODUCT_ID))
                .isInstanceOf(CoreException.class)
                .extracting("errorCode")
                .isEqualTo(LikeErrorCode.ALREADY_LIKED);
    }

    @Test
    @DisplayName("cancel: active Like 가 있으면 delete (deletedAt 채워짐)")
    void givenActiveLike_whenCancel_thenMarksAsDeleted() {
        Like active = Like.create(USER_ID, PRODUCT_ID);
        when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.of(active));

        likeService.cancel(USER_ID, PRODUCT_ID);

        assertThat(active.getDeletedAt()).isNotNull();
        verify(eventPublisher).publishEvent(new ProductLikeChangedEvent(PRODUCT_ID, -1L));
    }

    @Test
    @DisplayName("cancel: 이미 취소된 Like 라도 멱등 (deletedAt 그대로 유지)")
    void givenCancelledLike_whenCancel_thenRemainsCancelled() {
        Like cancelled = Like.create(USER_ID, PRODUCT_ID);
        cancelled.delete();
        var firstDeletedAt = cancelled.getDeletedAt();
        when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.of(cancelled));

        likeService.cancel(USER_ID, PRODUCT_ID);

        assertThat(cancelled.getDeletedAt()).isEqualTo(firstDeletedAt);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("cancel: Like 가 없어도 멱등 (예외 없이 종료)")
    void givenNoLike_whenCancel_thenDoesNothing() {
        when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.empty());

        likeService.cancel(USER_ID, PRODUCT_ID);

        verify(likeRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
