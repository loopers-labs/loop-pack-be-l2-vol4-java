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
    @DisplayName("醫뗭븘?붾? 泥섏쓬 ?깅줉?섎㈃ ?곹뭹 議댁옱 ?щ?瑜?寃利앺븯怨??대젰??異붽??쒕떎.")
    void addLike_NewLike_ShouldAddRecord() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        ProductModel product = new ProductModel(1L, "?곹뭹", new BigDecimal("1000"));
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
    @DisplayName("?대? 醫뗭븘?붾? ?꾨Ⅸ ?곹뭹???ㅼ떆 醫뗭븘?붾? ?붿껌?섎㈃ 異붽? 濡쒖쭅 ?놁씠 ?깃났?쒕떎. (硫깅벑??")
    void addLike_DuplicateLike_ShouldBeIdempotent() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        ProductModel product = new ProductModel(1L, "?곹뭹", new BigDecimal("1000"));
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
    @DisplayName("醫뗭븘?붾? 痍⑥냼?섎㈃ ?곹뭹 議댁옱 ?щ?瑜?寃利앺븯怨??대젰????젣?쒕떎.")
    void removeLike_ExistingLike_ShouldRemoveRecord() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        ProductModel product = new ProductModel(1L, "?곹뭹", new BigDecimal("1000"));
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
    @DisplayName("醫뗭븘?붾? ?꾨Ⅸ ???녿뒗 ?곹뭹??痍⑥냼瑜??붿껌?섎㈃ 異붽? 濡쒖쭅 ?놁씠 ?깃났?쒕떎. (硫깅벑??")
    void removeLike_NonExistentLike_ShouldBeIdempotent() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        ProductModel product = new ProductModel(1L, "?곹뭹", new BigDecimal("1000"));
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
