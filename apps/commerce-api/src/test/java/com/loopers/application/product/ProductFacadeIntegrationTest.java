package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.application.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.application.product.ProductRepository;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@ContextConfiguration(initializers = RedisTestContainersConfig.class)
class ProductFacadeIntegrationTest {

    @Autowired
    private ProductFacade productFacade;

    @Autowired
    private ProductAdminFacade productAdminFacade;

    @SpyBean
    private ProductRepository productRepository;

    @SpyBean
    private BrandRepository brandRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @SpyBean
    private RedisTemplate<String, String> defaultRedisTemplate;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        try {
            defaultRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        } catch (Exception ignored) {
            // SpyBean Mocking 상태 등으로 인한 예외 무시
        }
    }

    @Test
    @DisplayName("상품 상세를 처음 조회하면 DB에서 조회하고 캐시에 저장한다. 두 번째 조회하면 DB 조회 없이 캐시에서 반환한다.")
    void getProduct_ShouldCacheResult() {
        // given
        BrandModel brand = brandRepository.save(new BrandModel("Nike"));
        ProductModel product = productRepository.save(new ProductModel(brand.getId(), "Air Max", new BigDecimal("1000.00")));
        Long productId = product.getId();

        // when - 1차 조회 (Cache Miss, DB 조회 발생해야 함)
        ProductInfo firstResult = productFacade.getProduct(productId);

        // then - 1차 조회 검증
        assertThat(firstResult).isNotNull();
        assertThat(firstResult.name()).isEqualTo("Air Max");
        verify(productRepository, times(1)).findById(productId);
        verify(brandRepository, times(1)).findById(brand.getId());

        // when - 2차 조회 (Cache Hit, DB 조회 미발생해야 함)
        ProductInfo secondResult = productFacade.getProduct(productId);

        // then - 2차 조회 검증
        assertThat(secondResult).isNotNull();
        assertThat(secondResult.name()).isEqualTo("Air Max");
        // 전체 호출 횟수가 1회 그대로여야 함 (2차 호출 시 DB 안 거침)
        verify(productRepository, times(1)).findById(productId);
        verify(brandRepository, times(1)).findById(brand.getId());
    }

    @Test
    @DisplayName("Redis 연결 오류가 발생하더라도 예외를 던지지 않고 DB 조회를 통해 상품 상세 정보를 반환한다.")
    void getProduct_ShouldFallbackToDbOnRedisError() {
        // given
        BrandModel brand = brandRepository.save(new BrandModel("Nike"));
        ProductModel product = productRepository.save(new ProductModel(brand.getId(), "Air Max", new BigDecimal("1000.00")));
        Long productId = product.getId();

        // opsForValue() 호출 시 예외를 발생시키는 mock ValueOperations 설정
        org.springframework.data.redis.core.ValueOperations<String, String> mockValueOps = mock(org.springframework.data.redis.core.ValueOperations.class);
        doReturn(mockValueOps).when(defaultRedisTemplate).opsForValue();
        doThrow(new org.springframework.data.redis.RedisConnectionFailureException("Redis Connection Refused"))
                .when(mockValueOps).get(anyString());

        // when
        ProductInfo result = productFacade.getProduct(productId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Air Max");
        // Redis 에러가 삼켜지고 DB 조회가 발생했는지 검증
        verify(productRepository, atLeastOnce()).findById(productId);
    }

    @Test
    @DisplayName("상품 목록을 처음 조회하면 DB에서 조회하고 캐시에 저장한다. 두 번째 조회하면 DB 조회 없이 캐시에서 반환한다.")
    void getProducts_ShouldCacheResult() {
        // given
        BrandModel brand = brandRepository.save(new BrandModel("Nike"));
        productRepository.save(new ProductModel(brand.getId(), "Air Max", new BigDecimal("1000.00")));
        productRepository.save(new ProductModel(brand.getId(), "Cortez", new BigDecimal("800.00")));

        Long brandId = brand.getId();
        String sort = "latest";
        org.springframework.data.domain.Pageable pageRequest = org.springframework.data.domain.PageRequest.of(0, 10);

        // when - 1차 조회 (Cache Miss)
        org.springframework.data.domain.Page<ProductInfo> firstResult = productFacade.getProducts(brandId, sort, pageRequest);

        // then - 1차 검증
        assertThat(firstResult.getContent()).hasSize(2);
        verify(productRepository, times(1)).findAll(brandId, sort, pageRequest);

        // when - 2차 조회 (Cache Hit)
        org.springframework.data.domain.Page<ProductInfo> secondResult = productFacade.getProducts(brandId, sort, pageRequest);

        // then - 2차 검증
        assertThat(secondResult.getContent()).hasSize(2);
        verify(productRepository, times(1)).findAll(brandId, sort, pageRequest);
    }

    @Test
    @DisplayName("Redis 연결 오류가 발생하더라도 예외를 던지지 않고 DB 조회를 통해 상품 목록 정보를 반환한다.")
    void getProducts_ShouldFallbackToDbOnRedisError() {
        // given
        BrandModel brand = brandRepository.save(new BrandModel("Nike"));
        productRepository.save(new ProductModel(brand.getId(), "Air Max", new BigDecimal("1000.00")));

        Long brandId = brand.getId();
        String sort = "latest";
        org.springframework.data.domain.Pageable pageRequest = org.springframework.data.domain.PageRequest.of(0, 10);

        // opsForValue() 호출 시 예외를 발생시키는 mock ValueOperations 설정
        org.springframework.data.redis.core.ValueOperations<String, String> mockValueOps = mock(org.springframework.data.redis.core.ValueOperations.class);
        doReturn(mockValueOps).when(defaultRedisTemplate).opsForValue();
        doThrow(new org.springframework.data.redis.RedisConnectionFailureException("Redis Connection Refused"))
                .when(mockValueOps).get(anyString());

        // when
        org.springframework.data.domain.Page<ProductInfo> result = productFacade.getProducts(brandId, sort, pageRequest);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Air Max");
        // Redis 에러가 삼켜지고 DB 조회가 발생했는지 검증
        verify(productRepository, atLeastOnce()).findAll(brandId, sort, pageRequest);
    }

    @Test
    @DisplayName("상품 정보를 수정하면 해당 상품의 상세조회 캐시가 무효화되어 다음 조회 시 DB에서 새로 조회한다.")
    void updateProduct_ShouldEvictCache() {
        // given
        BrandModel brand = brandRepository.save(new BrandModel("Nike"));
        ProductModel product = productRepository.save(new ProductModel(brand.getId(), "Air Max", new BigDecimal("1000.00")));
        Long productId = product.getId();

        // 1. 상세 조회하여 캐시 적재 (DB 1회 호출)
        productFacade.getProduct(productId);
        verify(productRepository, times(1)).findById(productId);

        // 2. 상품 수정 실행 (캐시가 무효화되어야 함)
        productAdminFacade.updateProduct(productId, "Air Max Gold", new BigDecimal("1200.00"));

        // 3. 다시 상세 조회 (캐시 미스가 나야 하므로 DB를 다시 거침 -> 총 findById 2회 호출 기대)
        ProductInfo updatedInfo = productFacade.getProduct(productId);

        // then
        assertThat(updatedInfo.name()).isEqualTo("Air Max Gold");
        verify(productRepository, times(3)).findById(productId);
    }
}
