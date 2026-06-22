package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductFacadeCacheTest {

    @Autowired ProductFacade facade;
    @Autowired BrandJpaRepository brandJpaRepository;
    @Autowired ProductJpaRepository productJpaRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired DatabaseCleanUp databaseCleanUp;
    @Autowired RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("상세는 캐시되고(두번째 조회는 stale), 상품 수정 시 evict 되어 최신값이 보인다")
    @Test
    void detail_is_cached_and_evicted_on_update() {
        BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "Just Do It"));
        ProductModel product = productJpaRepository.save(new ProductModel(brand.getId(), "원래이름", "설명", 1000L, 10));
        Long id = product.getId();

        ProductDetailInfo first = facade.getProductDetail(id); // 캐시 적재

        // facade 를 우회해 DB 만 바꾼다 → 캐시 히트면 stale(원래이름) 이 나와야 한다
        jdbc.update("UPDATE product SET name = '직접변경' WHERE id = ?", id);
        ProductDetailInfo cachedHit = facade.getProductDetail(id);
        assertThat(cachedHit.name()).isEqualTo(first.name()); // 캐시 히트 증명(=원래이름)

        // facade 로 수정하면 evict → 다음 조회는 최신값
        facade.updateProduct(id, "정식변경", "설명", 2000L, 5);
        ProductDetailInfo afterEvict = facade.getProductDetail(id);
        assertThat(afterEvict.name()).isEqualTo("정식변경");
    }
}
