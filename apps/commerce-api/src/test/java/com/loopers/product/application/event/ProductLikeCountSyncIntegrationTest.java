package com.loopers.product.application.event;

import com.loopers.like.application.LikeService;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductStock;
import com.loopers.product.domain.ProductStockRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductLikeCountSyncIntegrationTest {

    private static final Long USER_ID = 1L;

    private final LikeService likeService;
    private final ProductRepository productRepository;
    private final ProductStockRepository productStockRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    ProductLikeCountSyncIntegrationTest(
            LikeService likeService,
            ProductRepository productRepository,
            ProductStockRepository productStockRepository,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.likeService = likeService;
        this.productRepository = productRepository;
        this.productStockRepository = productStockRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long saveOnSaleProduct() {
        Product product = productRepository.save(Product.create(1L, "상품", "설명", 1000L, null));
        productStockRepository.save(ProductStock.create(product.getId(), 10));
        return product.getId();
    }

    private long likeCountOf(Long productId) {
        return productRepository.findById(productId).orElseThrow().getLikeCount();
    }

    @Test
    @DisplayName("좋아요 등록 시 AFTER_COMMIT 리스너가 비동기로 like_count 를 +1 한다")
    void givenProduct_whenRegisterLike_thenLikeCountBecomesOne() {
        Long productId = saveOnSaleProduct();

        likeService.register(USER_ID, productId);

        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(likeCountOf(productId)).isEqualTo(1L));
    }

    @Test
    @DisplayName("좋아요 취소 시 like_count 가 -1 되어 0 으로 돌아온다")
    void givenLikedProduct_whenCancel_thenLikeCountBecomesZero() {
        Long productId = saveOnSaleProduct();
        likeService.register(USER_ID, productId);
        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(likeCountOf(productId)).isEqualTo(1L));

        likeService.cancel(USER_ID, productId);

        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(likeCountOf(productId)).isEqualTo(0L));
    }

    @Test
    @DisplayName("이미 좋아요한 상품을 다시 등록해도(상태 변화 없음) like_count 는 1 을 유지한다")
    void givenAlreadyLiked_whenRegisterAgain_thenLikeCountStaysOne() {
        Long productId = saveOnSaleProduct();
        likeService.register(USER_ID, productId);
        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(likeCountOf(productId)).isEqualTo(1L));

        likeService.register(USER_ID, productId);

        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(likeCountOf(productId)).isEqualTo(1L));
    }
}
