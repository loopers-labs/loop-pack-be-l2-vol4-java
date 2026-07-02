package com.loopers.application.product;

import com.loopers.application.like.LikeFacade;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.money.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSort;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import(RedisTestContainersConfig.class)
class ProductCacheIntegrationTest {

    @Autowired
    private ProductFacade productFacade;

    @Autowired
    private LikeFacade likeFacade;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    // 실제 빈을 감싸 DB 조회가 몇 번 일어났는지 센다. 캐시 적중이면 호출이 생략된다.
    @MockitoSpyBean
    private ProductRepository productRepository;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private Product saveProduct() {
        Brand brand = brandJpaRepository.save(new Brand("나이키", "Just Do It"));
        return productJpaRepository.save(new Product("에어맥스", "편한 러닝화",
            new Money(BigDecimal.valueOf(1000)), new Stock(10), brand.getId()));
    }

    @DisplayName("상품 상세를 두 번 조회하면, 두 번째는 캐시에서 응답해 DB 조회가 한 번만 발생한다.")
    @Test
    void hitsCache_onSecondDetailQuery() {
        Product product = saveProduct();

        productFacade.getProduct(product.getId());
        productFacade.getProduct(product.getId());

        verify(productRepository, times(1)).findById(product.getId());
    }

    @DisplayName("같은 조건으로 상품 목록을 두 번 조회하면, 두 번째는 캐시에서 응답해 DB 조회가 한 번만 발생한다.")
    @Test
    void hitsCache_onSecondListQuery() {
        Product product = saveProduct();
        Long brandId = product.getBrandId();

        productFacade.getProducts(brandId, "latest", 0, 20);
        productFacade.getProducts(brandId, "latest", 0, 20);

        verify(productRepository, times(1)).findAll(brandId, ProductSort.LATEST, 0, 20);
    }

    @DisplayName("좋아요가 변경되면 상세 캐시가 무효화되어, 다음 조회 시 갱신된 좋아요 수가 반영된다.")
    @Test
    void evictsDetailCache_whenLikeChanges() {
        Product product = saveProduct();

        ProductInfo before = productFacade.getProduct(product.getId());   // 좋아요 0 으로 캐시 적재
        likeFacade.like(1L, product.getId());                            // 캐시 무효화 + 집계는 비동기

        assertThat(before.likeCount()).isZero();
        // 집계가 비동기(@Async 리스너)라 즉시 조회는 갱신 전 값일 수 있다.
        // 집계 반영 후 핸들러가 캐시를 재무효화하므로, 조회가 갱신된 값으로 수렴할 때까지 기다려 검증한다.
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
            assertThat(productFacade.getProduct(product.getId()).likeCount()).isEqualTo(1L));
    }
}
