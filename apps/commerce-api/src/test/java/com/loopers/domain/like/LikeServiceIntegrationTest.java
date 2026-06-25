package com.loopers.domain.like;

import com.loopers.domain.product.ProductDescription;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductName;
import com.loopers.domain.product.ProductPrice;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductService;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LikeServiceIntegrationTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long saveProduct() {
        ProductModel product = productRepository.save(ProductModel.of(
                1L,
                ProductName.of("티셔츠"),
                ProductDescription.of("면 100%"),
                ProductPrice.of(10000L)
        ));
        return product.getId();
    }

    @DisplayName("좋아요 등록할 때")
    @Nested
    class Like {

        @DisplayName("복합키에 해당하는 데이터가 없으면, 행이 새로 생성되고 좋아요 상태가 된다.")
        @Test
        void createsRow_whenFirst() {
            // when
            likeService.like(1L, 2L);

            // then
            LikeModel found = likeRepository.find(LikeId.of(1L, 2L)).orElseThrow();
            assertThat(found.isLiked()).isTrue();
        }

        @DisplayName("복합키에 해당하는 데이터가 있고 취소 상태였으면, 다시 좋아요 상태가 된다.")
        @Test
        void resetsLikedAt_whenReLiked() {
            // given
            likeService.like(1L, 2L);
            likeService.unlike(1L, 2L);

            // when
            likeService.like(1L, 2L);

            // then
            LikeModel found = likeRepository.find(LikeId.of(1L, 2L)).orElseThrow();
            assertThat(found.isLiked()).isTrue();
        }

        @DisplayName("복합키에 해당하는 데이터가 있고 이미 좋아요 상태면, likedAt 시각이 유지된다.")
        @Test
        void keepsLikedAt_whenIdempotent() throws InterruptedException {
            // given
            likeService.like(1L, 2L);
            ZonedDateTime before = likeRepository.find(LikeId.of(1L, 2L)).orElseThrow().getLikedAt();
            Thread.sleep(10);

            // when
            likeService.like(1L, 2L);

            // then
            ZonedDateTime after = likeRepository.find(LikeId.of(1L, 2L)).orElseThrow().getLikedAt();
            assertThat(after).isEqualTo(before);
        }
    }

    @DisplayName("좋아요 취소할 때")
    @Nested
    class Unlike {

        @DisplayName("복합키에 해당하는 데이터가 있고 좋아요 상태였으면, 취소 상태(likedAt = null)가 된다.")
        @Test
        void clears_whenLiked() {
            // given
            likeService.like(1L, 2L);

            // when
            likeService.unlike(1L, 2L);

            // then
            LikeModel found = likeRepository.find(LikeId.of(1L, 2L)).orElseThrow();
            assertThat(found.isLiked()).isFalse();
        }

        @DisplayName("복합키에 해당하는 데이터가 있고 이미 취소 상태에서 다시 호출해도, 취소 상태가 유지된다.")
        @Test
        void keepsCancelled_whenIdempotent() {
            // given
            likeService.like(1L, 2L);
            likeService.unlike(1L, 2L);

            // when
            likeService.unlike(1L, 2L);

            // then
            LikeModel found = likeRepository.find(LikeId.of(1L, 2L)).orElseThrow();
            assertThat(found.isLiked()).isFalse();
        }

        @DisplayName("복합키에 해당하는 데이터가 없으면, 아무것도 하지 않는다.")
        @Test
        void doesNothing_whenNoRow() {
            // when
            likeService.unlike(1L, 2L);

            // then
            assertThat(likeRepository.find(LikeId.of(1L, 2L))).isEmpty();
        }
    }

    @DisplayName("좋아요 수(like_count) 동기화는")
    @Nested
    class LikeCountSync {

        @DisplayName("신규 좋아요 등록 시, 상품의 like_count가 1 증가한다.")
        @Test
        void increases_whenNewLike() {
            // given
            Long productId = saveProduct();

            // when
            likeService.like(1L, productId);

            // then
            assertThat(productService.getProduct(productId).getLikeCount()).isEqualTo(1L);
        }

        @DisplayName("이미 좋아요 상태에서 다시 등록해도, like_count는 그대로다.")
        @Test
        void keepsCount_whenAlreadyLiked() {
            // given
            Long productId = saveProduct();
            likeService.like(1L, productId);

            // when
            likeService.like(1L, productId);

            // then
            assertThat(productService.getProduct(productId).getLikeCount()).isEqualTo(1L);
        }

        @DisplayName("좋아요 취소 시, 상품의 like_count가 1 감소한다.")
        @Test
        void decreases_whenUnlike() {
            // given
            Long productId = saveProduct();
            likeService.like(1L, productId);

            // when
            likeService.unlike(1L, productId);

            // then
            assertThat(productService.getProduct(productId).getLikeCount()).isEqualTo(0L);
        }

        @DisplayName("좋아요가 없는 상태에서 취소해도, like_count는 음수가 되지 않는다.")
        @Test
        void neverNegative_whenUnlikeWithoutLike() {
            // given
            Long productId = saveProduct();

            // when
            likeService.unlike(1L, productId);

            // then
            assertThat(productService.getProduct(productId).getLikeCount()).isEqualTo(0L);
        }

        @DisplayName("서로 다른 유저가 좋아요하면, like_count가 각각 누적된다.")
        @Test
        void accumulates_acrossUsers() {
            // given
            Long productId = saveProduct();

            // when
            likeService.like(1L, productId);
            likeService.like(2L, productId);
            likeService.like(3L, productId);

            // then
            assertThat(productService.getProduct(productId).getLikeCount()).isEqualTo(3L);
        }
    }
}