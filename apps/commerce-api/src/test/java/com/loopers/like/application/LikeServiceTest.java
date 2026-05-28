package com.loopers.like.application;

import com.loopers.like.domain.Like;
import com.loopers.like.domain.LikeRepository;
import com.loopers.product.application.ProductReader;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LikeServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;

    private final LikeRepository likeRepository = mock(LikeRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ProductReader productReader = mock(ProductReader.class);
    private final LikeService likeService = new LikeService(likeRepository, productRepository, productReader);

    private Product activeProduct() {
        return Product.create(1L, "셔츠", "설명", 10_000L, null);
    }

    @Test
    @DisplayName("register: 상품 존재 + Like 없으면 새로 저장한다")
    void givenActiveProductAndNoLike_whenRegister_thenSavesNewLike() {
        when(productReader.getActive(PRODUCT_ID)).thenReturn(activeProduct());
        when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.empty());

        likeService.register(USER_ID, PRODUCT_ID);

        ArgumentCaptor<Like> captor = ArgumentCaptor.forClass(Like.class);
        verify(likeRepository).save(captor.capture());
        assertAll(
                () -> assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID),
                () -> assertThat(captor.getValue().getProductId()).isEqualTo(PRODUCT_ID)
        );
    }

    @Test
    @DisplayName("register: 이미 active Like 가 있으면 restore (deletedAt 그대로 null)")
    void givenActiveLike_whenRegister_thenRemainsActive() {
        Like active = Like.create(USER_ID, PRODUCT_ID);
        when(productReader.getActive(PRODUCT_ID)).thenReturn(activeProduct());
        when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.of(active));

        likeService.register(USER_ID, PRODUCT_ID);

        assertThat(active.getDeletedAt()).isNull();
        verify(likeRepository, never()).save(any());
    }

    @Test
    @DisplayName("register: 취소된 Like 가 있으면 restore (deletedAt 클리어)")
    void givenCancelledLike_whenRegister_thenRestoresLike() {
        Like cancelled = Like.create(USER_ID, PRODUCT_ID);
        cancelled.delete();
        when(productReader.getActive(PRODUCT_ID)).thenReturn(activeProduct());
        when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.of(cancelled));

        likeService.register(USER_ID, PRODUCT_ID);

        assertThat(cancelled.getDeletedAt()).isNull();
        verify(likeRepository, never()).save(any());
    }

    @Test
    @DisplayName("register: 삭제·판매중지 상품(getActive 미존재)이면 NOT_FOUND 가 전파되고 Like 저장하지 않는다")
    void givenNonActiveProduct_whenRegister_thenPropagatesNotFoundAndSavesNothing() {
        when(productReader.getActive(PRODUCT_ID))
                .thenThrow(new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

        assertThatThrownBy(() -> likeService.register(USER_ID, PRODUCT_ID))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND);

        verify(likeRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancel: active Like 가 있으면 delete (deletedAt 채워짐)")
    void givenActiveLike_whenCancel_thenMarksAsDeleted() {
        Like active = Like.create(USER_ID, PRODUCT_ID);
        when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.of(active));

        likeService.cancel(USER_ID, PRODUCT_ID);

        assertThat(active.getDeletedAt()).isNotNull();
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
    }

    @Test
    @DisplayName("cancel: Like 가 없어도 멱등 (예외 없이 종료)")
    void givenNoLike_whenCancel_thenDoesNothing() {
        when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.empty());

        likeService.cancel(USER_ID, PRODUCT_ID);

        verify(likeRepository, never()).save(any());
    }

    @Test
    @DisplayName("getMyLikes: 활성 like 의 productId 로 active product 들을 LikedProduct 로 매핑해 반환한다")
    void givenActiveLikes_whenGetMyLikes_thenReturnsLikedProducts() {
        Like l1 = Like.create(USER_ID, 10L);
        Like l2 = Like.create(USER_ID, 20L);
        Product p1 = Product.create(1L, "P1", "설명", 1000L, null);
        Product p2 = Product.create(1L, "P2", "설명", 2000L, null);
        when(likeRepository.findActiveByUserId(USER_ID)).thenReturn(List.of(l1, l2));
        when(productRepository.findAllByIdIn(List.of(10L, 20L))).thenReturn(List.of(p1, p2));

        List<LikeResult.LikedProduct> result = likeService.getMyLikes(USER_ID);

        assertThat(result)
                .extracting(LikeResult.LikedProduct::name)
                .containsExactly("P1", "P2");
    }

    @Test
    @DisplayName("getMyLikes: 활성 like 가 없으면 빈 리스트를 반환하고 product 조회를 안 한다")
    void givenNoActiveLikes_whenGetMyLikes_thenReturnsEmptyAndDoesNotQueryProducts() {
        when(likeRepository.findActiveByUserId(USER_ID)).thenReturn(List.of());

        List<LikeResult.LikedProduct> result = likeService.getMyLikes(USER_ID);

        assertThat(result).isEmpty();
        verify(productRepository, never()).findAllByIdIn(any());
    }
}
