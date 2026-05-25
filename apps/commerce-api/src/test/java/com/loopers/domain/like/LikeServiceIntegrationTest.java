package com.loopers.domain.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class LikeServiceIntegrationTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private LikeJpaRepository likeJpaRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long userId;
    private Long productId;

    @BeforeEach
    void setUp() {
        userId = 1L;
        BrandModel brand = brandRepository.save(new BrandModel("Loopers", "감성 라이프스타일 브랜드"));
        ProductModel product = productRepository.save(new ProductModel(brand, "후드", "포근함", 49_000L));
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요 등록 시")
    @Nested
    class Like {

        @DisplayName("정상 등록되면 product_like 행이 추가되고 product.likeCount는 1 증가한다")
        @Test
        void persistsLikeAndIncrementsCounter() {
            // when
            likeService.like(userId, productId);

            // then
            ProductModel product = productRepository.findById(productId).orElseThrow();
            assertAll(
                () -> assertThat(likeJpaRepository.existsByUserIdAndProductId(userId, productId)).isTrue(),
                () -> assertThat(product.getLikeCount()).isEqualTo(1)
            );
        }

        @DisplayName("이미 좋아요한 상태에서 다시 등록해도 멱등으로 likeCount는 그대로 1이다")
        @Test
        void isIdempotent_whenAlreadyLiked() {
            // given
            likeService.like(userId, productId);

            // when
            likeService.like(userId, productId);

            // then
            ProductModel product = productRepository.findById(productId).orElseThrow();
            assertAll(
                () -> assertThat(likeJpaRepository.count()).isEqualTo(1),
                () -> assertThat(product.getLikeCount()).isEqualTo(1)
            );
        }
    }

    @DisplayName("좋아요 취소 시")
    @Nested
    class Unlike {

        @DisplayName("실제로 삭제되면 product_like 행이 사라지고 likeCount가 1 감소한다")
        @Test
        void deletesLikeAndDecrementsCounter() {
            // given
            likeService.like(userId, productId);

            // when
            likeService.unlike(userId, productId);

            // then
            ProductModel product = productRepository.findById(productId).orElseThrow();
            assertAll(
                () -> assertThat(likeJpaRepository.existsByUserIdAndProductId(userId, productId)).isFalse(),
                () -> assertThat(product.getLikeCount()).isZero()
            );
        }

        @DisplayName("좋아요하지 않은 상품을 취소해도 멱등으로 likeCount는 0으로 유지된다")
        @Test
        void isIdempotent_whenNothingToUnlike() {
            // when
            likeService.unlike(userId, productId);

            // then
            ProductModel product = productRepository.findById(productId).orElseThrow();
            assertThat(product.getLikeCount()).isZero();
        }
    }
}
