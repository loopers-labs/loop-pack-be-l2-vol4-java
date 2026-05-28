package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
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
import org.springframework.http.ResponseEntity;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.like.LikeModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.user.PasswordEncrypter;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserLikeV1ApiE2ETest {

    private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    private static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";
    private static final String RAW_PASSWORD = "Kyle!2030";
    private static final ParameterizedTypeReference<ApiResponse<Map<String, Object>>> MAP_RESPONSE = new ParameterizedTypeReference<>() {};

    @Autowired
    private TestRestTemplate testRestTemplate;
    @Autowired
    private UserJpaRepository userJpaRepository;
    @Autowired
    private BrandJpaRepository brandJpaRepository;
    @Autowired
    private ProductJpaRepository productJpaRepository;
    @Autowired
    private LikeJpaRepository likeJpaRepository;
    @Autowired
    private PasswordEncrypter passwordEncrypter;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserModel saveUser(String loginId) {
        UserModel user = UserModel.builder()
            .rawLoginId(loginId)
            .rawPassword(RAW_PASSWORD)
            .rawName("테스트유저")
            .rawBirthDate(LocalDate.of(1995, 3, 21))
            .rawEmail(loginId + "@example.com")
            .passwordEncrypter(passwordEncrypter)
            .build();

        return userJpaRepository.save(user);
    }

    private BrandModel saveBrand(String name) {
        return brandJpaRepository.save(BrandModel.builder()
            .rawName(name)
            .rawDescription("감성을 담은 브랜드")
            .build());
    }

    private ProductModel saveProduct(Long brandId, String name) {
        return productJpaRepository.save(ProductModel.builder()
            .brandId(brandId)
            .rawName(name)
            .rawDescription("포근한 감성 가디건")
            .rawPrice(39_000)
            .rawStock(50)
            .build());
    }

    private void saveLike(Long userId, Long productId) {
        likeJpaRepository.save(LikeModel.builder()
            .userId(userId)
            .productId(productId)
            .build());
    }

    private HttpEntity<Void> memberRequest(String loginId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(LOGIN_ID_HEADER, loginId);
        headers.add(LOGIN_PW_HEADER, RAW_PASSWORD);

        return new HttpEntity<>(headers);
    }

    private HttpEntity<Void> unauthenticatedRequest() {
        return new HttpEntity<>(new HttpHeaders());
    }

    private String endpoint(Long userId) {
        return "/api/v1/users/" + userId + "/likes";
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> contentOf(ResponseEntity<ApiResponse<Map<String, Object>>> response) {
        return (List<Map<String, Object>>) response.getBody().data().get("content");
    }

    @DisplayName("좋아요한 상품 목록 - GET /api/v1/users/{userId}/likes")
    @Nested
    class ReadLikedProducts {

        @DisplayName("본인 식별자로 요청하면, 200 OK와 함께 좋아요한 상품과 페이지 메타가 반환된다.")
        @Test
        void returnsOk_withLikedProductsAndMeta() {
            // arrange
            UserModel user = saveUser("kylekim");
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "감성 가디건");
            saveLike(user.getId(), product.getId());

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                endpoint(user.getId()) + "?page=0&size=20",
                HttpMethod.GET,
                memberRequest("kylekim"),
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

        @DisplayName("좋아요한 상품이 최신 좋아요 순으로 반환된다.")
        @Test
        void sortsByLatestLike() {
            // arrange
            UserModel user = saveUser("kylekim");
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel firstLiked = saveProduct(brand.getId(), "먼저 좋아요한 상품");
            ProductModel lastLiked = saveProduct(brand.getId(), "나중에 좋아요한 상품");
            saveLike(user.getId(), firstLiked.getId());
            saveLike(user.getId(), lastLiked.getId());

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                endpoint(user.getId()) + "?page=0&size=20",
                HttpMethod.GET,
                memberRequest("kylekim"),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(contentOf(response))
                    .extracting(item -> ((Number) item.get("productId")).longValue())
                    .containsExactly(lastLiked.getId(), firstLiked.getId())
            );
        }

        @DisplayName("좋아요한 뒤 삭제된 상품은 목록에서 제외된다.")
        @Test
        void excludesDeletedProduct() {
            // arrange
            UserModel user = saveUser("kylekim");
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel activeProduct = saveProduct(brand.getId(), "활성 상품");
            ProductModel deletedProduct = saveProduct(brand.getId(), "삭제 상품");
            saveLike(user.getId(), activeProduct.getId());
            saveLike(user.getId(), deletedProduct.getId());
            deletedProduct.delete();
            productJpaRepository.saveAndFlush(deletedProduct);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                endpoint(user.getId()) + "?page=0&size=20",
                HttpMethod.GET,
                memberRequest("kylekim"),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(contentOf(response)).hasSize(1),
                () -> assertThat(((Number) contentOf(response).get(0).get("productId")).longValue())
                    .isEqualTo(activeProduct.getId())
            );
        }

        @DisplayName("좋아요한 상품이 없으면, 200 OK와 함께 빈 목록이 반환된다.")
        @Test
        void returnsOk_withEmptyContent() {
            // arrange
            UserModel user = saveUser("kylekim");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                endpoint(user.getId()) + "?page=0&size=20",
                HttpMethod.GET,
                memberRequest("kylekim"),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(contentOf(response)).isEmpty()
            );
        }

        @DisplayName("타인 식별자로 요청하면, 200 OK와 함께 빈 목록이 반환된다.")
        @Test
        void returnsOk_withEmptyContent_whenPathUserIsOther() {
            // arrange
            UserModel user = saveUser("kylekim");
            UserModel other = saveUser("otheruser");
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "감성 가디건");
            saveLike(other.getId(), product.getId());

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                endpoint(other.getId()) + "?page=0&size=20",
                HttpMethod.GET,
                memberRequest("kylekim"),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(contentOf(response)).isEmpty()
            );
        }

        @DisplayName("존재하지 않는 식별자로 요청해도, 200 OK와 함께 빈 목록이 반환된다.")
        @Test
        void returnsOk_withEmptyContent_whenPathUserIsAbsent() {
            // arrange
            saveUser("kylekim");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                endpoint(99999L) + "?page=0&size=20",
                HttpMethod.GET,
                memberRequest("kylekim"),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(contentOf(response)).isEmpty()
            );
        }

        @DisplayName("인증 헤더가 없으면, 401 Unauthorized로 거절된다.")
        @Test
        void returnsUnauthorized_whenAuthHeaderIsMissing() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                endpoint(1L) + "?page=0&size=20",
                HttpMethod.GET,
                unauthenticatedRequest(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.UNAUTHENTICATED.getCode())
            );
        }
    }
}
