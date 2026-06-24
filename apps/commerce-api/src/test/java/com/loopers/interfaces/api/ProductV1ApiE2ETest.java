package com.loopers.interfaces.api;

import com.loopers.interfaces.api.brand.BrandV1Dto;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

    private static final String PRODUCTS_PATH = "/api/v1/products";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public ProductV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long createBrand(String name) {
        ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                "/api/v1/brands", HttpMethod.POST,
                new HttpEntity<>(new BrandV1Dto.CreateBrandRequest(name, "설명")),
                new ParameterizedTypeReference<>() {});
        return response.getBody().data().id();
    }

    private Long createProduct(Long brandId, String name, Long price) {
        ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                PRODUCTS_PATH, HttpMethod.POST,
                new HttpEntity<>(new ProductV1Dto.CreateProductRequest(brandId, name, "설명", null, price, 10)),
                new ParameterizedTypeReference<>() {});
        return response.getBody().data().id();
    }

    private void deleteProduct(Long productId) {
        testRestTemplate.exchange(PRODUCTS_PATH + "/" + productId, HttpMethod.DELETE, HttpEntity.EMPTY, Object.class);
    }

    private static final ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductListPageResponse>> PAGE_TYPE =
            new ParameterizedTypeReference<>() {};

    private ProductV1Dto.ProductListPageResponse getList(String query) {
        return testRestTemplate.exchange(PRODUCTS_PATH + query, HttpMethod.GET, HttpEntity.EMPTY, PAGE_TYPE)
                .getBody().data();
    }

    @DisplayName("GET /api/v1/products — 목록·정렬·필터")
    @Nested
    class GetProducts {

        @DisplayName("정렬을 가격 오름차순으로 주면, 가격 오름차순으로 정렬되어 반환된다.")
        @Test
        void sortsByPriceAsc() {
            Long brandId = createBrand("나이키");
            createProduct(brandId, "B", 3000L);
            createProduct(brandId, "A", 1000L);
            createProduct(brandId, "C", 2000L);

            List<ProductV1Dto.ProductListItemResponse> data = getList("?sort=PRICE_ASC").items();

            assertAll(
                    () -> assertThat(data).hasSize(3),
                    () -> assertThat(data).extracting(ProductV1Dto.ProductListItemResponse::price)
                            .containsExactly(1000L, 2000L, 3000L)
            );
        }

        @DisplayName("브랜드 필터를 주면, 해당 브랜드의 상품만 반환된다.")
        @Test
        void filtersByBrand() {
            Long nike = createBrand("나이키");
            Long adidas = createBrand("아디다스");
            createProduct(nike, "에어맥스", 139000L);
            createProduct(adidas, "울트라부스트", 159000L);

            List<ProductV1Dto.ProductListItemResponse> data = getList("?brandId=" + nike).items();

            assertAll(
                    () -> assertThat(data).hasSize(1),
                    () -> assertThat(data.get(0).brandId()).isEqualTo(nike)
            );
        }

        @DisplayName("삭제된(비활성) 상품은 목록에서 제외된다.")
        @Test
        void excludesDeleted() {
            Long brandId = createBrand("나이키");
            Long keep = createProduct(brandId, "유지", 1000L);
            Long removed = createProduct(brandId, "삭제", 2000L);
            deleteProduct(removed);

            List<ProductV1Dto.ProductListItemResponse> data = getList("").items();

            assertAll(
                    () -> assertThat(data).hasSize(1),
                    () -> assertThat(data.get(0).id()).isEqualTo(keep)
            );
        }

        @DisplayName("커서 페이지네이션 — nextCursor로 다음 페이지를 이어 받으면 중복·누락 없이 전체를 순회한다. (LATEST)")
        @Test
        void paginatesByCursor() {
            Long brandId = createBrand("나이키");
            for (int i = 1; i <= 5; i++) {
                createProduct(brandId, "P" + i, 1000L * i);
            }

            ProductV1Dto.ProductListPageResponse p1 = getList("?sort=LATEST&size=2");
            assertThat(p1.items()).hasSize(2);
            assertThat(p1.hasNext()).isTrue();
            assertThat(p1.nextCursor()).isNotBlank();

            ProductV1Dto.ProductListPageResponse p2 = getList("?sort=LATEST&size=2&cursor=" + p1.nextCursor());
            assertThat(p2.items()).hasSize(2);
            assertThat(p2.hasNext()).isTrue();

            ProductV1Dto.ProductListPageResponse p3 = getList("?sort=LATEST&size=2&cursor=" + p2.nextCursor());
            assertThat(p3.items()).hasSize(1);
            assertThat(p3.hasNext()).isFalse();
            assertThat(p3.nextCursor()).isNull();

            List<Long> allIds = Stream.of(p1, p2, p3)
                    .flatMap(p -> p.items().stream())
                    .map(ProductV1Dto.ProductListItemResponse::id)
                    .toList();
            assertThat(allIds).doesNotHaveDuplicates().hasSize(5);
        }

        @DisplayName("size가 상한(100)을 초과하면, 400을 반환한다. (§7.2)")
        @Test
        void returns400_whenSizeOverLimit() {
            ResponseEntity<Object> response = testRestTemplate.exchange(
                    PRODUCTS_PATH + "?size=101", HttpMethod.GET, HttpEntity.EMPTY, Object.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("위조된 커서를 주면, 400을 반환한다.")
        @Test
        void returns400_whenInvalidCursor() {
            ResponseEntity<Object> response = testRestTemplate.exchange(
                    PRODUCTS_PATH + "?cursor=not-a-valid-cursor", HttpMethod.GET, HttpEntity.EMPTY, Object.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("잘못된 정렬 값을 주면, 400을 반환한다.")
        @Test
        void returns400_whenInvalidSort() {
            ResponseEntity<Object> response = testRestTemplate.exchange(
                    PRODUCTS_PATH + "?sort=INVALID", HttpMethod.GET, HttpEntity.EMPTY, Object.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("목록 응답에 각 상품의 브랜드명이 포함된다. (batch 조합)")
        @Test
        void includesBrandName() {
            Long brandId = createBrand("나이키");
            createProduct(brandId, "에어맥스", 139000L);

            List<ProductV1Dto.ProductListItemResponse> items = getList("").items();

            assertThat(items).hasSize(1);
            assertThat(items.get(0).brandName()).isEqualTo("나이키");
        }
    }

    // --- 좋아요 여부 표시 (UC-03 step3) ---

    private HttpHeaders authHeaders(String loginId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", "testPw1234");
        return headers;
    }

    private void signUp(String loginId, String name) {
        testRestTemplate.postForEntity("/api/v1/users",
                new UserV1Dto.SignUpRequest(loginId, "testPw1234", name, LocalDate.of(1992, 6, 24), "test@example.com"),
                Object.class);
    }

    private void like(Long productId, String loginId) {
        testRestTemplate.exchange(PRODUCTS_PATH + "/" + productId + "/like", HttpMethod.POST,
                new HttpEntity<>(authHeaders(loginId)), Object.class);
    }

    private List<ProductV1Dto.ProductListItemResponse> getListWithAuth(String loginId) {
        return testRestTemplate.exchange(PRODUCTS_PATH, HttpMethod.GET,
                        new HttpEntity<>(authHeaders(loginId)), PAGE_TYPE)
                .getBody().data().items();
    }

    private boolean likedOf(List<ProductV1Dto.ProductListItemResponse> items, Long productId) {
        return items.stream().filter(i -> i.id().equals(productId)).findFirst().orElseThrow().liked();
    }

    @DisplayName("GET /api/v1/products — 좋아요 여부 표시")
    @Nested
    class LikedFlag {

        @DisplayName("식별된 User는 본인이 좋아요한 상품만 liked=true로 본다.")
        @Test
        void userSeesLikedFlag() {
            signUp("testid", "테스터");
            Long brandId = createBrand("나이키");
            Long liked = createProduct(brandId, "에어맥스", 139000L);
            Long notLiked = createProduct(brandId, "조던", 199000L);
            like(liked, "testid");

            List<ProductV1Dto.ProductListItemResponse> items = getListWithAuth("testid");

            assertThat(likedOf(items, liked)).isTrue();
            assertThat(likedOf(items, notLiked)).isFalse();
        }

        @DisplayName("Guest(미인증)는 모든 상품을 liked=false로 본다.")
        @Test
        void guestSeesAllFalse() {
            signUp("testid", "테스터");
            Long brandId = createBrand("나이키");
            Long productId = createProduct(brandId, "에어맥스", 139000L);
            like(productId, "testid");

            List<ProductV1Dto.ProductListItemResponse> items = getList("").items();

            assertThat(items).isNotEmpty();
            assertThat(items).allSatisfy(i -> assertThat(i.liked()).isFalse());
        }
    }
}
