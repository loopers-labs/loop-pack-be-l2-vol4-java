package com.loopers.application.like;

import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LikeFacadeTest {

    @InjectMocks
    private LikeFacade likeFacade;

    @Mock
    private LikeService likeService;

    @Mock
    private ProductService productService;

    @Test
    @DisplayName("좋아요를 처음 등록하면 상품 존재 여부를 검증하고 이력이 추가된다.")
    void addLike_NewLike_ShouldAddRecord() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        ProductModel product = new ProductModel(1L, "상품", new BigDecimal("1000"));
        given(productService.getProduct(productId)).willReturn(product);
        given(likeService.existsLikeRecord(userId, productId)).willReturn(false);
        
        // when
        likeFacade.addLike(userId, productId);

        // then
        verify(productService).getProduct(productId);
        verify(likeService).existsLikeRecord(userId, productId);
        verify(likeService).addLikeRecord(userId, productId);
    }

    @Test
    @DisplayName("이미 좋아요를 누른 상품에 다시 좋아요를 요청하면 추가 로직 없이 성공한다. (멱등성)")
    void addLike_DuplicateLike_ShouldBeIdempotent() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        ProductModel product = new ProductModel(1L, "상품", new BigDecimal("1000"));
        given(productService.getProduct(productId)).willReturn(product);
        given(likeService.existsLikeRecord(userId, productId)).willReturn(true);

        // when
        likeFacade.addLike(userId, productId);

        // then
        verify(productService).getProduct(productId);
        verify(likeService).existsLikeRecord(userId, productId);
        verify(likeService, never()).addLikeRecord(userId, productId);
    }

    @Test
    @DisplayName("좋아요를 취소하면 상품 존재 여부를 검증하고 이력이 삭제된다.")
    void removeLike_ExistingLike_ShouldRemoveRecord() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        ProductModel product = new ProductModel(1L, "상품", new BigDecimal("1000"));
        given(productService.getProduct(productId)).willReturn(product);
        given(likeService.existsLikeRecord(userId, productId)).willReturn(true);

        // when
        likeFacade.removeLike(userId, productId);

        // then
        verify(productService).getProduct(productId);
        verify(likeService).existsLikeRecord(userId, productId);
        verify(likeService).removeLikeRecord(userId, productId);
    }

    @Test
    @DisplayName("좋아요를 누른 적 없는 상품에 취소를 요청하면 추가 로직 없이 성공한다. (멱등성)")
    void removeLike_NonExistentLike_ShouldBeIdempotent() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        ProductModel product = new ProductModel(1L, "상품", new BigDecimal("1000"));
        given(productService.getProduct(productId)).willReturn(product);
        given(likeService.existsLikeRecord(userId, productId)).willReturn(false);

        // when
        likeFacade.removeLike(userId, productId);

        // then
        verify(productService).getProduct(productId);
        verify(likeService).existsLikeRecord(userId, productId);
        verify(likeService, never()).removeLikeRecord(userId, productId);
    }
}
