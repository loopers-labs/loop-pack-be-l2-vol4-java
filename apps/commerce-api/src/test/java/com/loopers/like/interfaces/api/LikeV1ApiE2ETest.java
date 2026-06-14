package com.loopers.like.interfaces.api;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandService;
import com.loopers.like.domain.LikeService;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductService;
import com.loopers.stock.domain.ProductStockService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.product.interfaces.api.ProductV1Dto;
import com.loopers.user.interfaces.api.UserV1Dto;
import com.loopers.utils.DatabaseCleanUp;
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
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest {

    private static final String ENDPOINT_USERS = "/api/v1/users";
    private static final String ENDPOINT_USER_LIKES = "/api/v1/users/{userId}/likes";
    private static final String ENDPOINT_PRODUCT_DETAIL = "/api/v1/products/{productId}";
    private static final String ENDPOINT_PRODUCT_LIKES = "/api/v1/products/{productId}/likes";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final String LOGIN_ID = "loopers01";
    private static final String PASSWORD = "Loopers!2026";
    private static final String OTHER_LOGIN_ID = "jungwon01";
    private static final String OTHER_PASSWORD = "Jungwon!2026";
    private static final String OTHER_NAME = "정원이";
    private static final LocalDate OTHER_BIRTH_DATE = LocalDate.of(2004, 5, 25);
    private static final String OTHER_EMAIL = "jungwon@example.com";

    private final TestRestTemplate testRestTemplate;
    private final BrandService brandService;
    private final LikeService likeService;
    private final ProductService productService;
    private final ProductStockService productStockService;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    LikeV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandService brandService,
        LikeService likeService,
        ProductService productService,
        ProductStockService productStockService,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandService = brandService;
        this.likeService = likeService;
        this.productService = productService;
        this.productStockService = productStockService;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/products/{productId}/likes")
    @Nested
    class LikeProduct {

        @DisplayName("인증 사용자와 미삭제 상품 ID가 주어지면 200 OK를 반환하고, 상품 좋아요 수가 1 증가한다.")
        @Test
        void likesProduct_whenAuthenticatedUserAndProductIdExist() {
            // arrange
            signUpUser();
            Product product = createProduct();

            // act
            ResponseEntity<ApiResponse<Object>> likeResponse = likeProduct(product.getId(), authHeaders());
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> detailResponse = getProduct(product.getId());

            // assert
            assertAll(
                () -> assertThat(likeResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(detailResponse.getBody().data().likeCount()).isEqualTo(1)
            );
        }

        @DisplayName("이미 좋아요한 상품에 다시 좋아요를 등록해도 200 OK를 반환하고, 상품 좋아요 수를 중복 증가시키지 않는다.")
        @Test
        void keepsLikeCount_whenProductIsAlreadyLiked() {
            // arrange
            signUpUser();
            Product product = createProduct();
            likeProduct(product.getId(), authHeaders());

            // act
            ResponseEntity<ApiResponse<Object>> likeResponse = likeProduct(product.getId(), authHeaders());
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> detailResponse = getProduct(product.getId());

            // assert
            assertAll(
                () -> assertThat(likeResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(detailResponse.getBody().data().likeCount()).isEqualTo(1)
            );
        }

        @DisplayName("존재하지 않는 상품 ID가 주어지면 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            // arrange
            signUpUser();

            // act
            ResponseEntity<ApiResponse<Object>> response = likeProduct(999_999L, authHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("삭제된 상품 ID가 주어지면 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenProductIsDeleted() {
            // arrange
            signUpUser();
            Product product = createProduct();
            productService.deleteProduct(product.getId());

            // act
            ResponseEntity<ApiResponse<Object>> response = likeProduct(product.getId(), authHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    @Nested
    class UnlikeProduct {

        @DisplayName("좋아요한 상품에 취소 요청을 보내면 200 OK를 반환하고, 상품 좋아요 수가 0으로 감소한다.")
        @Test
        void unlikesProduct_whenProductIsLiked() {
            // arrange
            signUpUser();
            Product product = createProduct();
            likeProduct(product.getId(), authHeaders());

            // act
            ResponseEntity<ApiResponse<Object>> unlikeResponse = unlikeProduct(product.getId(), authHeaders());
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> detailResponse = getProduct(product.getId());

            // assert
            assertAll(
                () -> assertThat(unlikeResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(detailResponse.getBody().data().likeCount()).isZero()
            );
        }

        @DisplayName("좋아요하지 않은 상품에 취소 요청을 보내도 200 OK를 반환하고, 상품 좋아요 수를 유지한다.")
        @Test
        void keepsLikeCount_whenProductIsNotLiked() {
            // arrange
            signUpUser();
            Product product = createProduct();

            // act
            ResponseEntity<ApiResponse<Object>> unlikeResponse = unlikeProduct(product.getId(), authHeaders());
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> detailResponse = getProduct(product.getId());

            // assert
            assertAll(
                () -> assertThat(unlikeResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(detailResponse.getBody().data().likeCount()).isZero()
            );
        }

        @DisplayName("존재하지 않는 상품 ID에 취소 요청을 보내도 200 OK를 반환한다.")
        @Test
        void returnsOk_whenProductDoesNotExist() {
            // arrange
            signUpUser();

            // act
            ResponseEntity<ApiResponse<Object>> response = unlikeProduct(999_999L, authHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("삭제된 상품 ID에 취소 요청을 보내도 200 OK를 반환하고, 기존 좋아요를 제거한다.")
        @Test
        void deletesLike_whenProductIsDeleted() {
            // arrange
            signUpUser();
            Product product = createProduct();
            likeProduct(product.getId(), authHeaders());
            productService.deleteProduct(product.getId());

            // act
            ResponseEntity<ApiResponse<Object>> response = unlikeProduct(product.getId(), authHeaders());

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(likeService.countProductLikes(product.getId())).isZero()
            );
        }
    }

    @DisplayName("GET /api/v1/users/{userId}/likes")
    @Nested
    class GetMyLikes {

        @DisplayName("인증 사용자와 본인 userId가 주어지면 200 OK와 좋아요한 상품 목록을 최신 좋아요순 Page로 반환한다.")
        @Test
        void returnsMyLikedProductsByLatestLike_whenAuthenticatedUserAndOwnUserIdAreProvided() {
            // arrange
            UserV1Dto.UserResponse user = signUpUser();
            Brand brand = createBrand();
            Product iphone = createProduct(brand, "아이폰 16 Pro", "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰", 1_550_000L, 10);
            Product iphoneMax = createProduct(brand, "아이폰 16 Pro Max", "더 큰 화면과 향상된 배터리를 제공하는 스마트폰", 1_900_000L, 5);
            likeProduct(iphone.getId(), authHeaders());
            likeProduct(iphoneMax.getId(), authHeaders());

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response = getMyLikes(user.id(), authHeaders());

            // assert
            PageResponse<ProductV1Dto.ProductResponse> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.totalElements()).isEqualTo(2),
                () -> assertThat(data.content())
                    .extracting(ProductV1Dto.ProductResponse::name)
                    .containsExactly("아이폰 16 Pro Max", "아이폰 16 Pro"),
                () -> assertThat(data.content())
                    .extracting(ProductV1Dto.ProductResponse::likeCount)
                    .containsExactly(1L, 1L),
                () -> assertThat(data.content())
                    .extracting(product -> product.brand().name())
                    .containsExactly("애플", "애플")
            );
        }

        @DisplayName("경로 userId와 인증 사용자가 다르면 403 FORBIDDEN을 반환한다.")
        @Test
        void returnsForbidden_whenPathUserIdDoesNotMatchAuthenticatedUser() {
            // arrange
            UserV1Dto.UserResponse user = signUpUser();
            signUpUser(OTHER_LOGIN_ID, OTHER_PASSWORD, OTHER_NAME, OTHER_BIRTH_DATE, OTHER_EMAIL);

            // act
            ResponseEntity<ApiResponse<Object>> response = getMyLikesForError(
                user.id(),
                authHeaders(OTHER_LOGIN_ID, OTHER_PASSWORD)
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @DisplayName("좋아요한 상품이 삭제되면 목록에서 제외한다.")
        @Test
        void excludesDeletedProduct_whenLikedProductIsDeleted() {
            // arrange
            UserV1Dto.UserResponse user = signUpUser();
            Brand brand = createBrand();
            Product iphone = createProduct(brand, "아이폰 16 Pro", "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰", 1_550_000L, 10);
            Product iphoneMax = createProduct(brand, "아이폰 16 Pro Max", "더 큰 화면과 향상된 배터리를 제공하는 스마트폰", 1_900_000L, 5);
            likeProduct(iphone.getId(), authHeaders());
            likeProduct(iphoneMax.getId(), authHeaders());
            productService.deleteProduct(iphoneMax.getId());

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response = getMyLikes(user.id(), authHeaders());

            // assert
            PageResponse<ProductV1Dto.ProductResponse> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.totalElements()).isEqualTo(1),
                () -> assertThat(data.content())
                    .extracting(ProductV1Dto.ProductResponse::name)
                    .containsExactly("아이폰 16 Pro")
            );
        }

        @DisplayName("좋아요한 상품의 브랜드가 삭제되면 목록에서 제외한다.")
        @Test
        void excludesProductWithDeletedBrand_whenLikedProductBrandIsDeleted() {
            // arrange
            UserV1Dto.UserResponse user = signUpUser();
            Brand visibleBrand = createBrand();
            Brand deletedBrand = createBrand();
            Product iphone = createProduct(visibleBrand, "아이폰 16 Pro", "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰", 1_550_000L, 10);
            Product iphoneMax = createProduct(deletedBrand, "아이폰 16 Pro Max", "더 큰 화면과 향상된 배터리를 제공하는 스마트폰", 1_900_000L, 5);
            likeProduct(iphone.getId(), authHeaders());
            likeProduct(iphoneMax.getId(), authHeaders());
            brandService.deleteBrand(deletedBrand.getId());

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response = getMyLikes(user.id(), authHeaders());

            // assert
            PageResponse<ProductV1Dto.ProductResponse> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.totalElements()).isEqualTo(1),
                () -> assertThat(data.content())
                    .extracting(ProductV1Dto.ProductResponse::name)
                    .containsExactly("아이폰 16 Pro")
            );
        }
    }

    private Product createProduct() {
        return createProduct(
            createBrand(),
            "아이폰 16 Pro",
            "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
            1_550_000L,
            10
        );
    }

    private Brand createBrand() {
        return brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
    }

    private Product createProduct(Brand brand, String name, String description, long price, int stockQuantity) {
        Product product = productService.createProduct(
            brand.getId(),
            name,
            description,
            price
        );
        productStockService.createProductStock(product.getId(), stockQuantity);
        return product;
    }

    private UserV1Dto.UserResponse signUpUser() {
        return signUpUser(
            LOGIN_ID,
            PASSWORD,
            "김성호",
            LocalDate.of(1993, 11, 3),
            "loopers@example.com"
        );
    }

    private UserV1Dto.UserResponse signUpUser(String loginId, String password, String name, LocalDate birthDate, String email) {
        UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
            loginId,
            password,
            name,
            birthDate,
            email
        );
        ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_USERS,
            HttpMethod.POST,
            new HttpEntity<>(request),
            responseType
        ).getBody().data();
    }

    private ResponseEntity<ApiResponse<Object>> likeProduct(Long productId, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_PRODUCT_LIKES,
            HttpMethod.POST,
            new HttpEntity<>(headers),
            responseType,
            productId
        );
    }

    private ResponseEntity<ApiResponse<Object>> unlikeProduct(Long productId, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_PRODUCT_LIKES,
            HttpMethod.DELETE,
            new HttpEntity<>(headers),
            responseType,
            productId
        );
    }

    private ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> getProduct(Long productId) {
        ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_PRODUCT_DETAIL,
            HttpMethod.GET,
            null,
            responseType,
            productId
        );
    }

    private ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> getMyLikes(Long userId, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_USER_LIKES,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            responseType,
            userId
        );
    }

    private ResponseEntity<ApiResponse<Object>> getMyLikesForError(Long userId, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_USER_LIKES,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            responseType,
            userId
        );
    }

    private HttpHeaders authHeaders() {
        return authHeaders(LOGIN_ID, PASSWORD);
    }

    private HttpHeaders authHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, loginId);
        headers.set(HEADER_LOGIN_PW, password);
        return headers;
    }
}
