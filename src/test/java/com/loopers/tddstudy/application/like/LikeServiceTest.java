package com.loopers.tddstudy.application.like;

import com.loopers.tddstudy.domain.product.Product;
import com.loopers.tddstudy.support.FakeLikeRepository;
import com.loopers.tddstudy.support.FakeProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import com.loopers.tddstudy.domain.like.event.ProductLikedEvent;
import com.loopers.tddstudy.domain.like.event.ProductUnlikedEvent;
import java.util.ArrayList;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class LikeServiceTest {

    private FakeLikeRepository fakeLikeRepository;
    private FakeProductRepository fakeProductRepository;
    private LikeService likeService;

    private Product savedProduct;

    private final List<Object> publishedEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        fakeLikeRepository = new FakeLikeRepository();
        fakeProductRepository = new FakeProductRepository();
        publishedEvents.clear();
        ApplicationEventPublisher publisher = publishedEvents::add;

        likeService = new LikeService(fakeLikeRepository, fakeProductRepository, publisher);

        savedProduct = fakeProductRepository.save(new Product("나이키 운동화", 50000, 10, 1L));
    }

    @Test
    @DisplayName("처음 좋아요 시 ProductLikedEvent 가 발행된다")
    void add_like_publishes_event() {
        likeService.addLike(1L, savedProduct.getId());
        assertThat(publishedEvents).anyMatch(e -> e instanceof ProductLikedEvent);
    }

    @Test
    @DisplayName("이미 좋아요한 상품에 다시 좋아요하면 이벤트가 발행되지 않는다")
    void add_like_idempotent_no_event() {
        likeService.addLike(1L, savedProduct.getId());
        publishedEvents.clear();

        likeService.addLike(1L, savedProduct.getId());   // 두 번째

        assertThat(publishedEvents).noneMatch(e -> e instanceof ProductLikedEvent);
        assertThat(fakeLikeRepository.findAllByUserId(1L)).hasSize(1);  // 중복 저장 안 됨
    }

    @Test
    @DisplayName("좋아요 취소 시 ProductUnlikedEvent 가 발행된다")
    void cancel_like_publishes_event() {
        likeService.addLike(1L, savedProduct.getId());
        publishedEvents.clear();

        likeService.cancelLike(1L, savedProduct.getId());

        assertThat(publishedEvents).anyMatch(e -> e instanceof ProductUnlikedEvent);
    }


    @Test
    @DisplayName("존재하지 않는 상품에 좋아요 시 예외가 발생한다")
    void add_like_nonexistent_product_throws_exception() {
        assertThatThrownBy(() -> likeService.addLike(1L, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("상품을 찾을 수 없습니다.");
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
