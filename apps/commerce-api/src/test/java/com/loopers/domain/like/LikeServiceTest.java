package com.loopers.domain.like;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @InjectMocks
    private LikeService likeService;

    @Mock
    private LikeRepository likeRepository;

    @Test
    @DisplayName("醫뗭븘???대젰 異붽?瑜??붿껌?섎㈃ 由ы룷吏?좊━????λ맂??")
    void addLikeRecord_ShouldSave() {
        // given
        Long userId = 1L;
        Long productId = 10L;

        // when
        likeService.addLikeRecord(userId, productId);

        // then
        verify(likeRepository).save(any(ProductLikeModel.class));
    }
    @Test
    @DisplayName("?좊땲???쒖빟議곌굔(uk_product_likes_user_product) ?덉쇅 諛쒖깮 ??臾댁떆?섍퀬 ?뺤긽 醫낅즺?쒕떎.")
    void addLikeRecord_WhenUniqueConstraintViolation_ShouldIgnore() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        org.springframework.dao.DataIntegrityViolationException exception = 
            new org.springframework.dao.DataIntegrityViolationException("uk_product_likes_user_product violation");
        given(likeRepository.save(any(ProductLikeModel.class))).willThrow(exception);

        // when & then
        org.assertj.core.api.Assertions.assertThatCode(() -> {
            likeService.addLikeRecord(userId, productId);
        }).doesNotThrowAnyException();
        
        verify(likeRepository).save(any(ProductLikeModel.class));
    }

    @Test
    @DisplayName("?좊땲???쒖빟議곌굔 ?몄쓽 臾닿껐???덉쇅 諛쒖깮 ??CoreException?쇰줈 ?꾪뙆?쒕떎.")
    void addLikeRecord_WhenOtherIntegrityViolation_ShouldThrowCoreException() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        org.springframework.dao.DataIntegrityViolationException exception = 
            new org.springframework.dao.DataIntegrityViolationException("data too long for column");
        given(likeRepository.save(any(ProductLikeModel.class))).willThrow(exception);

        // when & then
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            likeService.addLikeRecord(userId, productId);
        })
        .isInstanceOf(com.loopers.support.error.CoreException.class)
        .hasMessageContaining("醫뗭븘???깅줉 以?臾닿껐???덉쇅媛 諛쒖깮?덉뒿?덈떎.")
        .hasCause(exception);
        
        verify(likeRepository).save(any(ProductLikeModel.class));
    }

    @Test
    @DisplayName("醫뗭븘???대젰 ??젣瑜??붿껌?섎㈃ 由ы룷吏?좊━????젣 硫붿꽌?쒓? ?몄텧?쒕떎.")
    void removeLikeRecord_ShouldDelete() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        given(likeRepository.findByUserIdAndProductId(userId, productId))
                .willReturn(Optional.of(new ProductLikeModel(userId, productId)));

        // when
        likeService.removeLikeRecord(userId, productId);

        // then
        verify(likeRepository).delete(any(ProductLikeModel.class));
    }

    @Test
    @DisplayName("?닿? 醫뗭븘?????곹뭹 紐⑸줉??議고쉶?섎㈃ ?꾩껜 紐⑸줉??諛섑솚?쒕떎.")
    void getMyLikes_ShouldReturnList() {
        // given
        Long userId = 1L;
        ProductLikeModel like1 = new ProductLikeModel(userId, 10L);
        ProductLikeModel like2 = new ProductLikeModel(userId, 20L);
        given(likeRepository.findAllByUserId(userId)).willReturn(java.util.List.of(like1, like2));

        // when
        java.util.List<ProductLikeModel> result = likeService.getMyLikes(userId);

        // then
        assertThat(result).hasSize(2);
        verify(likeRepository).findAllByUserId(userId);
    }

    @Test
    @DisplayName("醫뗭븘???대젰??議댁옱?섎㈃ true瑜?諛섑솚?쒕떎.")
    void existsLikeRecord_WhenExists_ShouldReturnTrue() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        given(likeRepository.findByUserIdAndProductId(userId, productId))
                .willReturn(Optional.of(new ProductLikeModel(userId, productId)));

        // when
        boolean result = likeService.existsLikeRecord(userId, productId);

        // then
        assertThat(result).isTrue();
        verify(likeRepository).findByUserIdAndProductId(userId, productId);
    }

    @Test
    @DisplayName("醫뗭븘???대젰???놁쑝硫?false瑜?諛섑솚?쒕떎.")
    void existsLikeRecord_WhenNotExists_ShouldReturnFalse() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        given(likeRepository.findByUserIdAndProductId(userId, productId))
                .willReturn(Optional.empty());

        // when
        boolean result = likeService.existsLikeRecord(userId, productId);

        // then
        assertThat(result).isFalse();
        verify(likeRepository).findByUserIdAndProductId(userId, productId);
    }
}
