package com.loopers.interfaces.api;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.productrank.ProductRank;
import com.loopers.domain.productrank.ProductRankRepository;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductCursorV1ApiE2ETest {

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final ProductRankRepository productRankRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public ProductCursorV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        ProductRankRepository productRankRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.productRankRepository = productRankRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private final ParameterizedTypeReference<ApiResponse<ProductV1Dto.CursorPageResponse>> responseType =
        new ParameterizedTypeReference<>() {};

    @DisplayName("GET /api/v1/products/cursor 는 좋아요순으로 페이지를 주고 nextCursor 로 이어진다")
    @Test
    void cursor_endpoint_paginates_by_likes_desc() {
        BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "Just Do It"));
        ProductModel p1 = productJpaRepository.save(new ProductModel(brand.getId(), "p1", "d", 1000L, 10));
        ProductModel p2 = productJpaRepository.save(new ProductModel(brand.getId(), "p2", "d", 1000L, 10));
        ProductModel p3 = productJpaRepository.save(new ProductModel(brand.getId(), "p3", "d", 1000L, 10));
        productRankRepository.replaceAll(List.of(
            new ProductRank(p1.getId(), brand.getId(), 50L),
            new ProductRank(p2.getId(), brand.getId(), 30L),
            new ProductRank(p3.getId(), brand.getId(), 10L)
        ));

        // 1페이지
        ResponseEntity<ApiResponse<ProductV1Dto.CursorPageResponse>> r1 = testRestTemplate.exchange(
            "/api/v1/products/cursor?brandId=" + brand.getId() + "&size=2", HttpMethod.GET, null, responseType);

        assertAll(
            () -> assertThat(r1.getStatusCode().is2xxSuccessful()).isTrue(),
            () -> assertThat(r1.getBody().data().items())
                .extracting(ProductV1Dto.ProductResponse::id).containsExactly(p1.getId(), p2.getId()),
            () -> assertThat(r1.getBody().data().nextCursor()).isNotNull()
        );

        // 2페이지 (nextCursor 로 이어가기)
        String next = r1.getBody().data().nextCursor();
        ResponseEntity<ApiResponse<ProductV1Dto.CursorPageResponse>> r2 = testRestTemplate.exchange(
            "/api/v1/products/cursor?brandId=" + brand.getId() + "&size=2&cursor=" + next,
            HttpMethod.GET, null, responseType);

        assertThat(r2.getBody().data().items())
            .extracting(ProductV1Dto.ProductResponse::id).containsExactly(p3.getId());
    }
}
