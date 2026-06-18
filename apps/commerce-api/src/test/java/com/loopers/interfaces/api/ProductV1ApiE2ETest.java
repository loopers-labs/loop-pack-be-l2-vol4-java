package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.like.LikeModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/products";
    private static final ParameterizedTypeReference<ApiResponse<Map<String, Object>>> MAP_RESPONSE = new ParameterizedTypeReference<>() {};

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private LikeJpaRepository likeJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private BrandModel saveBrand(String name) {
        return brandJpaRepository.save(BrandModel.builder()
            .rawName(name)
            .rawDescription("감성을 담은 브랜드")
            .build());
    }

    private ProductModel saveProduct(Long brandId, String name, int price, int stock) {
        return productJpaRepository.save(ProductModel.builder()
            .brandId(brandId)
            .rawName(name)
            .rawDescription("포근한 감성 가디건")
            .rawPrice(price)
            .rawStock(stock)
            .build());
    }

    private void saveLike(Long userId, Long productId) {
        likeJpaRepository.save(LikeModel.builder()
            .userId(userId)
            .productId(productId)
            .build());
        productJpaRepository.incrementLikeCount(productId);
    }

    private HttpEntity<Void> guestGet() {
        return new HttpEntity<>(null);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> contentOf(ResponseEntity<ApiResponse<Map<String, Object>>> response) {
        return (List<Map<String, Object>>) response.getBody().data().get("content");
    }

    @DisplayName("상품 목록 - GET /api/v1/products")
    @Nested
    class ReadProducts {

        @DisplayName("정상 요청이면, 200 OK와 함께 상품 목록과 페이지 메타가 반환되고 각 항목은 재고 가용 여부·좋아요 수를 포함한다.")
        @Test
        void returnsOk_withProductsAndMeta() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "감성 가디건", 39_000, 5);
            saveLike(1L, product.getId());

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "?page=0&size=20",
                HttpMethod.GET,
                guestGet(),
                MAP_RESPONSE
            );

            // assert
            Map<String, Object> item = contentOf(response).get(0);
            Map<?, ?> itemBrand = (Map<?, ?>) item.get("brand");
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data())
                    .containsKeys("content", "page", "size", "totalElements", "totalPages"),
                () -> assertThat(contentOf(response)).hasSize(1),
                () -> assertThat(item).containsOnlyKeys("productId", "name", "brand", "price", "isAvailable", "likeCount"),
                () -> assertThat(((Number) item.get("productId")).longValue()).isEqualTo(product.getId()),
                () -> assertThat(item.get("name")).isEqualTo("감성 가디건"),
                () -> assertThat(((Number) item.get("price")).intValue()).isEqualTo(39_000),
                () -> assertThat(((Number) itemBrand.get("brandId")).longValue()).isEqualTo(brand.getId()),
                () -> assertThat(itemBrand.get("name")).isEqualTo("감성 브랜드"),
                () -> assertThat(item.get("isAvailable")).isEqualTo(true),
                () -> assertThat(((Number) item.get("likeCount")).intValue()).isEqualTo(1)
            );
        }

        @DisplayName("재고가 0인 상품은 가용 여부가 false로 노출되되 목록에는 포함된다.")
        @Test
        void marksUnavailable_whenStockIsZero() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            saveProduct(brand.getId(), "품절 상품", 39_000, 0);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "?page=0&size=20",
                HttpMethod.GET,
                guestGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(contentOf(response)).hasSize(1),
                () -> assertThat(contentOf(response).get(0).get("isAvailable")).isEqualTo(false)
            );
        }

        @DisplayName("좋아요 많은 순으로 요청하면, 좋아요 수 내림차순으로 정렬되어 반환된다.")
        @Test
        void sortsByLikesDesc() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel mostLiked = saveProduct(brand.getId(), "인기 상품", 39_000, 5);
            ProductModel leastLiked = saveProduct(brand.getId(), "비인기 상품", 39_000, 5);
            saveLike(1L, mostLiked.getId());
            saveLike(2L, mostLiked.getId());

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "?sort=likes_desc&page=0&size=20",
                HttpMethod.GET,
                guestGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(contentOf(response))
                    .extracting(item -> ((Number) item.get("productId")).longValue())
                    .containsExactly(mostLiked.getId(), leastLiked.getId())
            );
        }

        @DisplayName("가격 오름차순으로 요청하면, 가격이 낮은 순으로 정렬되어 반환된다.")
        @Test
        void sortsByPriceAsc() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            saveProduct(brand.getId(), "비싼 상품", 30_000, 5);
            saveProduct(brand.getId(), "싼 상품", 10_000, 5);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "?sort=price_asc&page=0&size=20",
                HttpMethod.GET,
                guestGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(contentOf(response))
                    .extracting(item -> ((Number) item.get("price")).intValue())
                    .containsExactly(10_000, 30_000)
            );
        }

        @DisplayName("brandId 필터를 지정하면, 해당 브랜드의 상품만 반환된다.")
        @Test
        void filtersByBrandId() {
            // arrange
            BrandModel brandA = saveBrand("브랜드 A");
            BrandModel brandB = saveBrand("브랜드 B");
            ProductModel productA = saveProduct(brandA.getId(), "A 상품", 39_000, 5);
            saveProduct(brandB.getId(), "B 상품", 39_000, 5);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "?brandId=" + brandA.getId() + "&page=0&size=20",
                HttpMethod.GET,
                guestGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(contentOf(response)).hasSize(1),
                () -> assertThat(((Number) contentOf(response).get(0).get("productId")).longValue())
                    .isEqualTo(productA.getId())
            );
        }

        @DisplayName("조건에 맞는 상품이 없으면, 200 OK와 함께 빈 목록이 반환된다.")
        @Test
        void returnsOk_withEmptyContent() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "?page=0&size=20",
                HttpMethod.GET,
                guestGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(contentOf(response)).isEmpty(),
                () -> assertThat(((Number) response.getBody().data().get("totalElements")).longValue()).isEqualTo(0L)
            );
        }

        @DisplayName("최신 등록 순으로 요청하면, 등록 시각 내림차순(가장 최근 등록이 먼저)으로 정렬되어 반환된다.")
        @Test
        void sortsByLatest() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel firstRegistered = saveProduct(brand.getId(), "먼저 등록된 상품", 20_000, 5);
            ProductModel lastRegistered = saveProduct(brand.getId(), "나중 등록된 상품", 30_000, 5);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "?page=0&size=20",
                HttpMethod.GET,
                guestGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(contentOf(response))
                    .extracting(item -> ((Number) item.get("productId")).longValue())
                    .containsExactly(lastRegistered.getId(), firstRegistered.getId())
            );
        }

        @DisplayName("허용되지 않는 정렬 값이면, 400 Bad Request로 거절된다.")
        @Test
        void returnsBadRequest_whenSortIsInvalid() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "?sort=unknown&page=0&size=20",
                HttpMethod.GET,
                guestGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode())
            );
        }

    }

    @DisplayName("상품 상세 - GET /api/v1/products/{productId}")
    @Nested
    class ReadProduct {

        @DisplayName("정상 요청이면, 200 OK와 함께 설명·재고 가용 여부·좋아요 수를 포함한 상세가 반환되고 관리자 전용 필드는 포함되지 않는다.")
        @Test
        void returnsOk_withDetail() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "감성 가디건", 39_000, 5);
            saveLike(1L, product.getId());

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + product.getId(),
                HttpMethod.GET,
                guestGet(),
                MAP_RESPONSE
            );

            // assert
            Map<String, Object> data = response.getBody().data();
            Map<?, ?> dataBrand = (Map<?, ?>) data.get("brand");
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(data)
                    .containsOnlyKeys("productId", "name", "description", "brand", "price", "isAvailable", "likeCount"),
                () -> assertThat(((Number) data.get("productId")).longValue()).isEqualTo(product.getId()),
                () -> assertThat(data.get("name")).isEqualTo("감성 가디건"),
                () -> assertThat(data.get("description")).isEqualTo("포근한 감성 가디건"),
                () -> assertThat(((Number) data.get("price")).intValue()).isEqualTo(39_000),
                () -> assertThat(((Number) dataBrand.get("brandId")).longValue()).isEqualTo(brand.getId()),
                () -> assertThat(dataBrand.get("name")).isEqualTo("감성 브랜드"),
                () -> assertThat(data.get("isAvailable")).isEqualTo(true),
                () -> assertThat(((Number) data.get("likeCount")).intValue()).isEqualTo(1)
            );
        }

        @DisplayName("재고가 0인 상품이면, 200 OK와 함께 isAvailable=false로 반환된다.")
        @Test
        void returnsOk_withUnavailable_whenStockIsZero() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "품절 가디건", 39_000, 0);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + product.getId(),
                HttpMethod.GET,
                guestGet(),
                MAP_RESPONSE
            );

            // assert
            Map<String, Object> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(data.get("isAvailable")).isEqualTo(false)
            );
        }

        @DisplayName("존재하지 않는 상품이면, 404 Not Found로 거절된다.")
        @Test
        void returnsNotFound_whenProductIsAbsent() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/99999",
                HttpMethod.GET,
                guestGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.NOT_FOUND.getCode())
            );
        }

        @DisplayName("삭제된 상품이면, 404 Not Found로 거절된다.")
        @Test
        void returnsNotFound_whenProductIsDeleted() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "감성 가디건", 39_000, 5);
            product.delete();
            productJpaRepository.saveAndFlush(product);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + product.getId(),
                HttpMethod.GET,
                guestGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.NOT_FOUND.getCode())
            );
        }

        @DisplayName("상세를 연속 조회하면 첫 요청(캐시 미스)·둘째 요청(캐시 히트) 모두 200으로 동일한 좋아요 수를 반환한다.")
        @Test
        void returnsSameDetail_onMissThenHit() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "감성 가디건", 39_000, 50);
            saveLike(1L, product.getId());

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> first =
                testRestTemplate.exchange(ENDPOINT + "/" + product.getId(), HttpMethod.GET, guestGet(), MAP_RESPONSE);
            ResponseEntity<ApiResponse<Map<String, Object>>> second =
                testRestTemplate.exchange(ENDPOINT + "/" + product.getId(), HttpMethod.GET, guestGet(), MAP_RESPONSE);

            // assert
            assertAll(
                () -> assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(((Number) first.getBody().data().get("likeCount")).intValue()).isEqualTo(1),
                () -> assertThat(second.getBody().data().get("likeCount"))
                    .isEqualTo(first.getBody().data().get("likeCount"))
            );
        }
    }
}
