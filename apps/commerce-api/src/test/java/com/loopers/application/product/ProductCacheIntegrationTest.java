package com.loopers.application.product;

import com.loopers.domain.brand.BrandDescription;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandName;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductDescription;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductName;
import com.loopers.domain.product.ProductPrice;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductStatus;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductCacheIntegrationTest {

    @Autowired
    private ProductFacade productFacade;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private Long saveBrand() {
        return brandRepository.save(BrandModel.of(BrandName.of("나이키"), BrandDescription.of("브랜드 설명"))).getId();
    }

    private ProductModel saveProduct(Long brandId, String name, Long price) {
        return productRepository.save(ProductModel.of(
                brandId,
                ProductName.of(name),
                ProductDescription.of(name + " 설명"),
                ProductPrice.of(price)
        ));
    }

    @DisplayName("상품 상세는 캐시되어, 캐시를 무효화하지 않은 채 DB만 바꾸면 직전 값이 그대로 반환된다.")
    @Test
    void productDetail_isCached() {
        // given
        Long brandId = saveBrand();
        ProductModel product = saveProduct(brandId, "원본", 1000L);
        productFacade.getProductDetail(product.getId()); // 캐시 적재

        // when — 서비스(=evict)를 우회해 DB만 직접 변경
        product.update(brandId, ProductName.of("변경"), ProductDescription.of("변경 설명"), ProductPrice.of(2000L), ProductStatus.ON_SALE);
        productRepository.save(product);
        ProductDetailInfo cached = productFacade.getProductDetail(product.getId());

        // then — 캐시가 응답하므로 직전(원본) 값
        assertThat(cached.name()).isEqualTo("원본");
        assertThat(cached.price()).isEqualTo(1000L);
    }

    @DisplayName("좋아요 수 변경 시 상세 캐시가 무효화되어, 재조회 시 갱신된 likeCount가 반환된다.")
    @Test
    void productDetail_isEvicted_onLikeCountChange() {
        // given
        Long brandId = saveBrand();
        ProductModel product = saveProduct(brandId, "상품", 1000L);
        assertThat(productFacade.getProductDetail(product.getId()).likeCount()).isZero(); // 캐시 적재

        // when — 좋아요 수 증가(= @CacheEvict)
        productService.increaseLikeCount(product.getId());

        // then — 캐시가 비워져 갱신값을 읽는다
        assertThat(productFacade.getProductDetail(product.getId()).likeCount()).isEqualTo(1L);
    }

    @DisplayName("상품 목록은 캐시되어, 무효화 전이라면 새로 추가된 상품이 반영되지 않는다.")
    @Test
    void productList_isCached() {
        // given
        Long brandId = saveBrand();
        saveProduct(brandId, "A", 1000L);
        Page<ProductSummaryInfo> first = productFacade.getProducts(null, null, null, PageRequest.of(0, 20));
        assertThat(first.getTotalElements()).isEqualTo(1L); // 캐시 적재

        // when — 같은 조건의 목록 캐시를 무효화하지 않고 상품 추가
        saveProduct(brandId, "B", 2000L);
        Page<ProductSummaryInfo> second = productFacade.getProducts(null, null, null, PageRequest.of(0, 20));

        // then — 캐시가 응답하므로 여전히 1건
        assertThat(second.getTotalElements()).isEqualTo(1L);
    }
}