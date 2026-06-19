package com.loopers.domain.product;

import com.loopers.application.like.LikeFacade;
import com.loopers.config.TestPasswordEncoderConfig;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.stock.StockService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.fixture.BrandFixture;
import com.loopers.fixture.ProductFixture;
import com.loopers.fixture.UserFixture;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import({RedisTestContainersConfig.class, TestPasswordEncoderConfig.class})
class ProductServiceCacheIntegrationTest {

    @Autowired private ProductService productService;
    @Autowired private BrandService brandService;
    @Autowired private StockService stockService;
    @Autowired private UserService userService;
    @Autowired private LikeFacade likeFacade;
    @Autowired private DatabaseCleanUp databaseCleanUp;
    @Autowired private RedisCleanUp redisCleanUp;

    @MockitoSpyBean
    private ProductRepository productRepository;

    private UUID productId;
    private UserModel user;

    @BeforeEach
    void setUp() {
        BrandModel brand = brandService.create(BrandFixture.NAME, BrandFixture.DESCRIPTION);
        ProductModel product = productService.create(brand, ProductFixture.NAME, ProductFixture.DESCRIPTION, ProductFixture.PRICE);
        stockService.create(product.getId(), ProductFixture.INITIAL_QUANTITY);
        productId = product.getId();
        user = userService.register(UserFixture.createModel());
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("상품 상세 조회 캐시")
    @Nested
    class GetActiveSnapshotCache {

        @DisplayName("첫 번째 조회 시 DB를 조회하고 캐시에 저장한다.")
        @Test
        void cacheMiss_queriesDB_onFirstCall() {
            productService.getActiveSnapshot(productId);

            verify(productRepository, times(1)).findActive(productId);
        }

        @DisplayName("두 번째 조회 시 캐시를 히트하여 DB를 조회하지 않는다.")
        @Test
        void cacheHit_skipsDB_onSecondCall() {
            productService.getActiveSnapshot(productId); // cache miss → DB → 캐시 저장
            clearInvocations(productRepository);

            productService.getActiveSnapshot(productId); // cache hit

            verify(productRepository, never()).findActive(any());
        }

        @DisplayName("좋아요 발생 시 캐시가 무효화되어 다음 조회는 DB를 조회한다.")
        @Test
        void like_evictsCache_nextCallQueriesDB() {
            productService.getActiveSnapshot(productId); // 캐시 저장
            likeFacade.like(productId, user);            // @CacheEvict
            clearInvocations(productRepository);

            productService.getActiveSnapshot(productId); // cache miss → DB

            verify(productRepository, times(1)).findActive(productId);
        }

        @DisplayName("좋아요 취소 발생 시 캐시가 무효화되어 다음 조회는 DB를 조회한다.")
        @Test
        void unlike_evictsCache_nextCallQueriesDB() {
            likeFacade.like(productId, user);            // 좋아요 먼저
            productService.getActiveSnapshot(productId); // 캐시 저장
            likeFacade.unlike(productId, user);          // @CacheEvict
            clearInvocations(productRepository);

            productService.getActiveSnapshot(productId); // cache miss → DB

            verify(productRepository, times(1)).findActive(productId);
        }
    }
}
