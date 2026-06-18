package com.loopers.application.like;

import com.loopers.application.product.ProductRepository;
import com.loopers.domain.like.ProductLikeModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LikeFacadeTest {

    @InjectMocks
    private LikeFacade likeFacade;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private ProductRepository productRepository;

    @Test
    @DisplayName("좋아요를 처음 등록하면 상품 존재 여부를 검증하고 이력이 추가된다.")
    void addLike_NewLike_ShouldAddRecord() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        ProductModel product = new ProductModel(1L, "상품", new BigDecimal("1000"));
        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(likeRepository.findByUserIdAndProductId(userId, productId)).willReturn(Optional.empty());
        
        // when
        likeFacade.addLike(userId, productId);

        // then
        verify(productRepository).findById(productId);
        verify(likeRepository).findByUserIdAndProductId(userId, productId);
        verify(likeRepository).save(any(ProductLikeModel.class));
    }

    @Test
    @DisplayName("이미 좋아요를 누른 상품에 다시 좋아요를 요청하면 추가 로직 없이 성공한다. (멱등성)")
    void addLike_DuplicateLike_ShouldBeIdempotent() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        ProductModel product = new ProductModel(1L, "상품", new BigDecimal("1000"));
        ProductLikeModel existingLike = new ProductLikeModel(userId, productId);
        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(likeRepository.findByUserIdAndProductId(userId, productId)).willReturn(Optional.of(existingLike));

        // when
        likeFacade.addLike(userId, productId);

        // then
        verify(productRepository).findById(productId);
        verify(likeRepository).findByUserIdAndProductId(userId, productId);
        verify(likeRepository, never()).save(any(ProductLikeModel.class));
    }

    @Test
    @DisplayName("좋아요를 취소하면 상품 존재 여부를 검증하고 이력이 삭제된다.")
    void removeLike_ExistingLike_ShouldRemoveRecord() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        ProductModel product = new ProductModel(1L, "상품", new BigDecimal("1000"));
        ProductLikeModel existingLike = new ProductLikeModel(userId, productId);
        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(likeRepository.findByUserIdAndProductId(userId, productId)).willReturn(Optional.of(existingLike));

        // when
        likeFacade.removeLike(userId, productId);

        // then
        verify(productRepository).findById(productId);
        verify(likeRepository).findByUserIdAndProductId(userId, productId);
        verify(likeRepository).delete(existingLike);
    }

    @Test
    @DisplayName("좋아요를 누른 적 없는 상품에 취소를 요청하면 추가 로직 없이 성공한다. (멱등성)")
    void removeLike_NonExistentLike_ShouldBeIdempotent() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        ProductModel product = new ProductModel(1L, "상품", new BigDecimal("1000"));
        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(likeRepository.findByUserIdAndProductId(userId, productId)).willReturn(Optional.empty());

        // when
        likeFacade.removeLike(userId, productId);

        // then
        verify(productRepository).findById(productId);
        verify(likeRepository).findByUserIdAndProductId(userId, productId);
        verify(likeRepository, never()).delete(any(ProductLikeModel.class));
    }
}
