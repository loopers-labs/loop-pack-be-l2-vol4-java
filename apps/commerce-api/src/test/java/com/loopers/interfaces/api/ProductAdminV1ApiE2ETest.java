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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.product.ProductAdminV1Dto;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductAdminV1ApiE2ETest {

    private static final String ENDPOINT = "/api-admin/v1/products";
    private static final String LDAP_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP = "loopers.admin";
    private static final ParameterizedTypeReference<ApiResponse<Map<String, Object>>> MAP_RESPONSE = new ParameterizedTypeReference<>() {};

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpEntity<Object> adminJsonRequest(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(LDAP_HEADER, ADMIN_LDAP);

        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Object> jsonRequestWithoutAdmin(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        return new HttpEntity<>(body, headers);
    }

    private BrandModel saveBrand(String name) {
        BrandModel brand = BrandModel.builder()
            .rawName(name)
            .rawDescription("감성을 담은 브랜드")
            .build();

        return brandJpaRepository.save(brand);
    }

    private ProductModel saveProduct(Long brandId, String name) {
        ProductModel product = ProductModel.builder()
            .brandId(brandId)
            .rawName(name)
            .rawDescription("포근한 감성 가디건")
            .rawPrice(39_000)
            .rawStock(50)
            .build();

        return productJpaRepository.save(product);
    }

    private HttpEntity<Void> adminGet() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(LDAP_HEADER, ADMIN_LDAP);

        return new HttpEntity<>(headers);
    }

    private HttpEntity<Void> guestGet() {
        return new HttpEntity<>(new HttpHeaders());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> contentOf(ResponseEntity<ApiResponse<Map<String, Object>>> response) {
        return (List<Map<String, Object>>) response.getBody().data().get("content");
    }

    @DisplayName("상품 목록 - GET /api-admin/v1/products")
    @Nested
    class ReadProducts {

        @DisplayName("정상 요청이면, 200 OK와 함께 삭제되지 않은 상품 목록과 페이지 메타가 반환되고 각 항목은 정확 재고·등록/갱신 시각을 포함한다.")
        @Test
        void returnsOk_withProductsAndMeta() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "상품1");
            ProductModel deletedProduct = saveProduct(brand.getId(), "삭제 상품");
            deletedProduct.delete();
            productJpaRepository.saveAndFlush(deletedProduct);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "?page=0&size=20",
                HttpMethod.GET,
                adminGet(),
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
                () -> assertThat(((Number) response.getBody().data().get("totalElements")).longValue()).isEqualTo(1L),
                () -> assertThat(item)
                    .containsOnlyKeys("productId", "name", "description", "brand", "price", "stock", "createdAt", "updatedAt"),
                () -> assertThat(((Number) item.get("productId")).longValue()).isEqualTo(product.getId()),
                () -> assertThat(item.get("name")).isEqualTo("상품1"),
                () -> assertThat(item.get("description")).isEqualTo("포근한 감성 가디건"),
                () -> assertThat(((Number) item.get("price")).intValue()).isEqualTo(39_000),
                () -> assertThat(((Number) item.get("stock")).intValue()).isEqualTo(50),
                () -> assertThat(((Number) itemBrand.get("brandId")).longValue()).isEqualTo(brand.getId()),
                () -> assertThat(itemBrand.get("name")).isEqualTo("감성 브랜드"),
                () -> assertThat(item.get("createdAt")).isNotNull(),
                () -> assertThat(item.get("updatedAt")).isNotNull()
            );
        }

        @DisplayName("brandId 필터를 지정하면, 해당 브랜드의 상품만 반환된다.")
        @Test
        void filtersByBrandId() {
            // arrange
            BrandModel brandA = saveBrand("브랜드 A");
            BrandModel brandB = saveBrand("브랜드 B");
            ProductModel productA = saveProduct(brandA.getId(), "A 상품");
            saveProduct(brandB.getId(), "B 상품");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "?brandId=" + brandA.getId() + "&page=0&size=20",
                HttpMethod.GET,
                adminGet(),
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

        @DisplayName("활성 상품이 없으면, 200 OK와 함께 빈 목록이 반환된다.")
        @Test
        void returnsOk_withEmptyContent() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "?page=0&size=20",
                HttpMethod.GET,
                adminGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(contentOf(response)).isEmpty()
            );
        }

        @DisplayName("관리자 인증 헤더가 없으면, 403 Forbidden으로 거절된다.")
        @Test
        void returnsForbidden_whenAdminHeaderIsMissing() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "?page=0&size=20",
                HttpMethod.GET,
                guestGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.FORBIDDEN.getCode())
            );
        }
    }

    @DisplayName("상품 상세 - GET /api-admin/v1/products/{productId}")
    @Nested
    class ReadProduct {

        @DisplayName("정상 요청이면, 200 OK와 함께 정확 재고·등록/갱신 시각을 포함한 상세가 반환된다.")
        @Test
        void returnsOk_withDetail() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "감성 가디건");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + product.getId(),
                HttpMethod.GET,
                adminGet(),
                MAP_RESPONSE
            );

            // assert
            Map<String, Object> data = response.getBody().data();
            Map<?, ?> dataBrand = (Map<?, ?>) data.get("brand");
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(data)
                    .containsOnlyKeys("productId", "name", "description", "brand", "price", "stock", "createdAt", "updatedAt"),
                () -> assertThat(((Number) data.get("productId")).longValue()).isEqualTo(product.getId()),
                () -> assertThat(data.get("name")).isEqualTo("감성 가디건"),
                () -> assertThat(data.get("description")).isEqualTo("포근한 감성 가디건"),
                () -> assertThat(((Number) data.get("price")).intValue()).isEqualTo(39_000),
                () -> assertThat(((Number) data.get("stock")).intValue()).isEqualTo(50),
                () -> assertThat(((Number) dataBrand.get("brandId")).longValue()).isEqualTo(brand.getId()),
                () -> assertThat(dataBrand.get("name")).isEqualTo("감성 브랜드"),
                () -> assertThat(data.get("createdAt")).isNotNull(),
                () -> assertThat(data.get("updatedAt")).isNotNull()
            );
        }

        @DisplayName("재고가 0인 상품이면, 200 OK와 함께 stock=0으로 반환된다.")
        @Test
        void returnsOk_withZeroStock_whenStockIsZero() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = productJpaRepository.save(ProductModel.builder()
                .brandId(brand.getId())
                .rawName("품절 가디건")
                .rawDescription("포근한 감성 가디건")
                .rawPrice(39_000)
                .rawStock(0)
                .build());

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + product.getId(),
                HttpMethod.GET,
                adminGet(),
                MAP_RESPONSE
            );

            // assert
            Map<String, Object> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(((Number) data.get("stock")).intValue()).isEqualTo(0)
            );
        }

        @DisplayName("관리자 인증 헤더가 없으면, 403 Forbidden으로 거절된다.")
        @Test
        void returnsForbidden_whenAdminHeaderIsMissing() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "감성 가디건");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + product.getId(),
                HttpMethod.GET,
                guestGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.FORBIDDEN.getCode())
            );
        }

        @DisplayName("존재하지 않는 상품이면, 404 Not Found로 거절된다.")
        @Test
        void returnsNotFound_whenProductIsAbsent() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/99999",
                HttpMethod.GET,
                adminGet(),
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
            ProductModel product = saveProduct(brand.getId(), "감성 가디건");
            product.delete();
            productJpaRepository.saveAndFlush(product);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + product.getId(),
                HttpMethod.GET,
                adminGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.NOT_FOUND.getCode())
            );
        }
    }

    @DisplayName("상품 등록 - POST /api-admin/v1/products")
    @Nested
    class CreateProduct {

        @DisplayName("정상 요청이면, 201 Created와 함께 productId가 응답 본문에 담겨 반환된다.")
        @Test
        void returnsCreated_whenRequestIsValid() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductAdminV1Dto.CreateRequest requestBody =
                new ProductAdminV1Dto.CreateRequest(brand.getId(), "감성 가디건", "포근한 감성 가디건", 39_000, 50);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data()).containsOnlyKeys("productId"),
                () -> assertThat(response.getBody().data().get("productId")).isNotNull(),
                () -> assertThat(productJpaRepository.findAll()).hasSize(1)
            );
        }

        @DisplayName("관리자 인증 헤더가 없으면, 403 Forbidden으로 거절되고 상품은 생성되지 않는다.")
        @Test
        void returnsForbidden_whenAdminHeaderIsMissing() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductAdminV1Dto.CreateRequest requestBody =
                new ProductAdminV1Dto.CreateRequest(brand.getId(), "감성 가디건", "포근한 감성 가디건", 39_000, 50);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                jsonRequestWithoutAdmin(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.FORBIDDEN.getCode()),
                () -> assertThat(productJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("지정한 브랜드가 존재하지 않으면, 404 Not Found로 거절되고 상품은 생성되지 않는다.")
        @Test
        void returnsNotFound_whenBrandIsAbsent() {
            // arrange
            ProductAdminV1Dto.CreateRequest requestBody =
                new ProductAdminV1Dto.CreateRequest(99999L, "감성 가디건", "포근한 감성 가디건", 39_000, 50);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.NOT_FOUND.getCode()),
                () -> assertThat(productJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("상품 이름이 100자를 초과하면, 400 Bad Request로 거절되고 상품은 생성되지 않는다.")
        @Test
        void returnsBadRequest_whenNameExceedsMaxLength() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductAdminV1Dto.CreateRequest requestBody =
                new ProductAdminV1Dto.CreateRequest(brand.getId(), "가".repeat(101), "포근한 감성 가디건", 39_000, 50);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode()),
                () -> assertThat(productJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("가격이 0 미만이면, 400 Bad Request로 거절되고 상품은 생성되지 않는다.")
        @Test
        void returnsBadRequest_whenPriceIsNegative() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductAdminV1Dto.CreateRequest requestBody =
                new ProductAdminV1Dto.CreateRequest(brand.getId(), "감성 가디건", "포근한 감성 가디건", -1, 50);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode()),
                () -> assertThat(productJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("재고가 0 미만이면, 400 Bad Request로 거절되고 상품은 생성되지 않는다.")
        @Test
        void returnsBadRequest_whenStockIsNegative() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductAdminV1Dto.CreateRequest requestBody =
                new ProductAdminV1Dto.CreateRequest(brand.getId(), "감성 가디건", "포근한 감성 가디건", 39_000, -1);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode()),
                () -> assertThat(productJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("가격과 재고가 0(경계 최소값)이면, 201 Created로 상품이 생성된다.")
        @Test
        void returnsCreated_whenPriceAndStockAreZero() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductAdminV1Dto.CreateRequest requestBody =
                new ProductAdminV1Dto.CreateRequest(brand.getId(), "감성 가디건", "포근한 감성 가디건", 0, 0);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data()).containsOnlyKeys("productId"),
                () -> assertThat(response.getBody().data().get("productId")).isNotNull(),
                () -> assertThat(productJpaRepository.findAll()).hasSize(1)
            );
        }
    }

    @DisplayName("상품 수정 - PUT /api-admin/v1/products/{productId}")
    @Nested
    class UpdateProduct {

        @DisplayName("정상 요청이면, 200 OK와 함께 productId가 반환되고 값이 갱신된다.")
        @Test
        void returnsOk_andUpdatesValues_whenRequestIsValid() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel savedProduct = saveProduct(brand.getId(), "감성 가디건");
            ProductAdminV1Dto.UpdateRequest requestBody =
                new ProductAdminV1Dto.UpdateRequest("리뉴얼 가디건", "새 설명", 42_000, 30);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + savedProduct.getId(),
                HttpMethod.PUT,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            ProductModel reloadedProduct = productJpaRepository.findById(savedProduct.getId()).orElseThrow();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data().get("productId")).isNotNull(),
                () -> assertThat(reloadedProduct.getName().value()).isEqualTo("리뉴얼 가디건"),
                () -> assertThat(reloadedProduct.getPrice().value()).isEqualTo(42_000),
                () -> assertThat(reloadedProduct.getStock().value()).isEqualTo(30)
            );
        }

        @DisplayName("설명을 null로 수정하면, 200 OK와 함께 설명이 null로 갱신된다.")
        @Test
        void returnsOk_andUpdatesDescriptionToNull_whenDescriptionIsNull() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel savedProduct = saveProduct(brand.getId(), "감성 가디건");
            ProductAdminV1Dto.UpdateRequest requestBody =
                new ProductAdminV1Dto.UpdateRequest("리뉴얼 가디건", null, 42_000, 30);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + savedProduct.getId(),
                HttpMethod.PUT,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            ProductModel reloadedProduct = productJpaRepository.findById(savedProduct.getId()).orElseThrow();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(reloadedProduct.getDescription()).isNull()
            );
        }

        @DisplayName("가격 0·재고 0(경계 최소값)으로 수정하면, 200 OK와 함께 정상 갱신된다.")
        @Test
        void returnsOk_whenPriceAndStockAreZero() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel savedProduct = saveProduct(brand.getId(), "감성 가디건");
            ProductAdminV1Dto.UpdateRequest requestBody =
                new ProductAdminV1Dto.UpdateRequest("리뉴얼 가디건", "새 설명", 0, 0);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + savedProduct.getId(),
                HttpMethod.PUT,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            ProductModel reloadedProduct = productJpaRepository.findById(savedProduct.getId()).orElseThrow();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(reloadedProduct.getPrice().value()).isEqualTo(0),
                () -> assertThat(reloadedProduct.getStock().value()).isEqualTo(0)
            );
        }

        @DisplayName("관리자 인증 헤더가 없으면, 403 Forbidden으로 거절된다.")
        @Test
        void returnsForbidden_whenAdminHeaderIsMissing() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel savedProduct = saveProduct(brand.getId(), "감성 가디건");
            ProductAdminV1Dto.UpdateRequest requestBody =
                new ProductAdminV1Dto.UpdateRequest("리뉴얼 가디건", "새 설명", 42_000, 30);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + savedProduct.getId(),
                HttpMethod.PUT,
                jsonRequestWithoutAdmin(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.FORBIDDEN.getCode())
            );
        }

        @DisplayName("대상 상품이 존재하지 않으면, 404 Not Found로 거절된다.")
        @Test
        void returnsNotFound_whenTargetIsAbsent() {
            // arrange
            ProductAdminV1Dto.UpdateRequest requestBody =
                new ProductAdminV1Dto.UpdateRequest("리뉴얼 가디건", "새 설명", 42_000, 30);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/99999",
                HttpMethod.PUT,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.NOT_FOUND.getCode())
            );
        }

        @DisplayName("이름이 100자를 초과하면, 400 Bad Request로 거절된다.")
        @Test
        void returnsBadRequest_whenNameExceedsMaxLength() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel savedProduct = saveProduct(brand.getId(), "감성 가디건");
            ProductAdminV1Dto.UpdateRequest requestBody =
                new ProductAdminV1Dto.UpdateRequest("가".repeat(101), "새 설명", 42_000, 30);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + savedProduct.getId(),
                HttpMethod.PUT,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode())
            );
        }

        @DisplayName("이름이 빈 값(blank)이면, 400 Bad Request로 거절된다.")
        @Test
        void returnsBadRequest_whenNameIsBlank() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel savedProduct = saveProduct(brand.getId(), "감성 가디건");
            ProductAdminV1Dto.UpdateRequest requestBody =
                new ProductAdminV1Dto.UpdateRequest("   ", "새 설명", 42_000, 30);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + savedProduct.getId(),
                HttpMethod.PUT,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode())
            );
        }

        @DisplayName("가격이 0 미만이면, 400 Bad Request로 거절된다.")
        @Test
        void returnsBadRequest_whenPriceIsNegative() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel savedProduct = saveProduct(brand.getId(), "감성 가디건");
            ProductAdminV1Dto.UpdateRequest requestBody =
                new ProductAdminV1Dto.UpdateRequest("리뉴얼 가디건", "새 설명", -1, 30);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + savedProduct.getId(),
                HttpMethod.PUT,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode())
            );
        }

        @DisplayName("재고가 0 미만이면, 400 Bad Request로 거절된다.")
        @Test
        void returnsBadRequest_whenStockIsNegative() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel savedProduct = saveProduct(brand.getId(), "감성 가디건");
            ProductAdminV1Dto.UpdateRequest requestBody =
                new ProductAdminV1Dto.UpdateRequest("리뉴얼 가디건", "새 설명", 42_000, -1);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + savedProduct.getId(),
                HttpMethod.PUT,
                adminJsonRequest(requestBody),
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

    @DisplayName("상품 삭제 - DELETE /api-admin/v1/products/{productId}")
    @Nested
    class DeleteProduct {

        @DisplayName("정상 요청이면, 200 OK로 처리되고 해당 상품은 활성 목록·상세 조회에서 제외된다.")
        @Test
        void returnsOk_andSoftDeletes_whenRequestIsValid() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel savedProduct = saveProduct(brand.getId(), "감성 가디건");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> deleteResponse = testRestTemplate.exchange(
                ENDPOINT + "/" + savedProduct.getId(),
                HttpMethod.DELETE,
                adminJsonRequest(null),
                MAP_RESPONSE
            );

            // assert — 삭제 응답
            assertAll(
                () -> assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(deleteResponse.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)
            );

            // assert — 활성 목록에서 제외됨
            ResponseEntity<ApiResponse<Map<String, Object>>> listResponse = testRestTemplate.exchange(
                ENDPOINT + "?page=0&size=20",
                HttpMethod.GET,
                adminGet(),
                MAP_RESPONSE
            );
            assertThat(contentOf(listResponse)).isEmpty();

            // assert — 활성 상세에서 404
            ResponseEntity<ApiResponse<Map<String, Object>>> detailResponse = testRestTemplate.exchange(
                ENDPOINT + "/" + savedProduct.getId(),
                HttpMethod.GET,
                adminGet(),
                MAP_RESPONSE
            );
            assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("동일한 상품에 삭제를 두 번 요청해도, 두 응답 모두 200 OK로 마무리된다(멱등).")
        @Test
        void returnsOk_whenDeletedTwice() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel savedProduct = saveProduct(brand.getId(), "감성 가디건");
            String endpoint = ENDPOINT + "/" + savedProduct.getId();

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> firstResponse = testRestTemplate.exchange(
                endpoint, HttpMethod.DELETE, adminJsonRequest(null), MAP_RESPONSE);
            ResponseEntity<ApiResponse<Map<String, Object>>> secondResponse = testRestTemplate.exchange(
                endpoint, HttpMethod.DELETE, adminJsonRequest(null), MAP_RESPONSE);

            // assert
            assertAll(
                () -> assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(firstResponse.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(secondResponse.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)
            );
        }

        @DisplayName("대상 상품이 존재하지 않아도, 200 OK로 마무리된다(멱등).")
        @Test
        void returnsOk_whenTargetIsAbsent() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/99999",
                HttpMethod.DELETE,
                adminJsonRequest(null),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)
            );
        }

        @DisplayName("관리자 인증 헤더가 없으면, 403 Forbidden으로 거절된다.")
        @Test
        void returnsForbidden_whenAdminHeaderIsMissing() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel savedProduct = saveProduct(brand.getId(), "감성 가디건");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + savedProduct.getId(),
                HttpMethod.DELETE,
                jsonRequestWithoutAdmin(null),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.FORBIDDEN.getCode())
            );
        }
    }
}
