package com.loopers.tddstudy.application.like;

import com.loopers.tddstudy.domain.product.Product;
import com.loopers.tddstudy.support.FakeLikeRepository;
import com.loopers.tddstudy.support.FakeProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class LikeServiceTest {

    private FakeLikeRepository fakeLikeRepository;
    private FakeProductRepository fakeProductRepository;
    private LikeService likeService;

    private Product savedProduct;

    @BeforeEach
    void setUp() {
        fakeLikeRepository = new FakeLikeRepository();
        fakeProductRepository = new FakeProductRepository();
        likeService = new LikeService(fakeLikeRepository, fakeProductRepository);

        savedProduct = fakeProductRepository.save(new Product("나이키 운동화", 50000, 10, 1L));
    }

    @Test
    @DisplayName("처음 좋아요 시 likeCount 가 1 증가한다")
    void add_like_increases_like_count() {
        likeService.addLike(1L, savedProduct.getId());

        Product product = fakeProductRepository.findById(savedProduct.getId()).get();
        assertThat(product.getLikeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 좋아요한 상품에 다시 좋아요해도 likeCount 는 증가하지 않는다")
    void add_like_idempotent() {
        likeService.addLike(1L, savedProduct.getId());
        likeService.addLike(1L, savedProduct.getId());

        Product product = fakeProductRepository.findById(savedProduct.getId()).get();
        assertThat(product.getLikeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("존재하지 않는 상품에 좋아요 시 예외가 발생한다")
    void add_like_nonexistent_product_throws_exception() {
        assertThatThrownBy(() -> likeService.addLike(1L, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("상품을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("좋아요 취소 시 likeCount 가 1 감소한다")
    void cancel_like_decreases_like_count() {
        likeService.addLike(1L, savedProduct.getId());

        likeService.cancelLike(1L, savedProduct.getId());

        Product product = fakeProductRepository.findById(savedProduct.getId()).get();
        assertThat(product.getLikeCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("좋아요하지 않은 상품을 취소해도 예외가 발생하지 않는다")
    void cancel_like_idempotent() {
        assertThatCode(() -> likeService.cancelLike(1L, savedProduct.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("내 좋아요 목록을 조회할 수 있다")
    void get_my_likes_success() {
        Product savedProduct2 = fakeProductRepository.save(new Product("나이키 티셔츠", 30000, 5, 1L));

        likeService.addLike(1L, savedProduct.getId());
        likeService.addLike(1L, savedProduct2.getId());

        List<Long> likedProductIds = likeService.getMyLikes(1L);

        assertThat(likedProductIds).hasSize(2);
        assertThat(likedProductIds).contains(savedProduct.getId(), savedProduct2.getId());
    }
}
