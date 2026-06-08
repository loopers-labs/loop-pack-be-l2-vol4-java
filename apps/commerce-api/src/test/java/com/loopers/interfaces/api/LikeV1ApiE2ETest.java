package com.loopers.interfaces.api;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.stock.StockJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.like.LikeV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest {

    private static final String RAW_PASSWORD = "Password1!";

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private StockJpaRepository stockJpaRepository;
    @Autowired private LikeJpaRepository likeJpaRepository;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private UserModel testUser;
    private ProductModel savedProduct;

    @BeforeEach
    void setUp() {
        testUser = userJpaRepository.save(new UserModel(
            "testuser", passwordEncoder.encode(RAW_PASSWORD),
            "테스터", LocalDate.of(1990, 1, 15), "test@example.com"
        ));
        BrandModel brand = brandJpaRepository.save(new BrandModel("Nike", "스포츠 브랜드"));
        savedProduct = productJpaRepository.save(new ProductModel(brand, "나이키 에어맥스", 150_000));
        stockJpaRepository.save(new StockModel(savedProduct, 100));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", testUser.getLoginId());
        headers.set("X-Loopers-LoginPw", RAW_PASSWORD);
        return headers;
    }

    private String likeUrl(Long productId) {
        return "/api/v1/products/" + productId + "/likes";
    }

    private String userLikesUrl(Long userId) {
        return "/api/v1/users/" + userId + "/likes";
    }

    @DisplayName("POST /api/v1/products/{productId}/likes 요청 시,")
    @Nested
    class Like {

        @DisplayName("좋아요 등록 성공 시 200이 반환된다.")
        @Test
        void returns200_whenLikeRegistered() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                likeUrl(savedProduct.getId()), HttpMethod.POST,
                new HttpEntity<>(authHeaders()), new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("이미 좋아요한 상품에 재요청 시 200이 반환된다 (멱등).")
        @Test
        void returns200_whenLikedAlready() {
            HttpEntity<Void> req = new HttpEntity<>(authHeaders());
            testRestTemplate.exchange(likeUrl(savedProduct.getId()), HttpMethod.POST, req, new ParameterizedTypeReference<>() {});

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                likeUrl(savedProduct.getId()), HttpMethod.POST, req, new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("존재하지 않는 상품에 좋아요 시 404가 반환된다.")
        @Test
        void returns404_whenProductDoesNotExist() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                likeUrl(999L), HttpMethod.POST,
                new HttpEntity<>(authHeaders()), new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("삭제된 상품에 좋아요 시 400이 반환된다.")
        @Test
        void returns400_whenProductIsDeleted() {
            savedProduct.delete();
            productJpaRepository.save(savedProduct);

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                likeUrl(savedProduct.getId()), HttpMethod.POST,
                new HttpEntity<>(authHeaders()), new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}/likes 요청 시,")
    @Nested
    class Unlike {

        @DisplayName("좋아요 취소 성공 시 200이 반환된다.")
        @Test
        void returns200_whenUnliked() {
            HttpEntity<Void> req = new HttpEntity<>(authHeaders());
            testRestTemplate.exchange(likeUrl(savedProduct.getId()), HttpMethod.POST, req, new ParameterizedTypeReference<>() {});

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                likeUrl(savedProduct.getId()), HttpMethod.DELETE, req, new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("좋아요 없는 상태에서 취소 시 200이 반환된다 (멱등).")
        @Test
        void returns200_whenUnlikedWithNoLike() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                likeUrl(savedProduct.getId()), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders()), new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @DisplayName("GET /api/v1/users/{userId}/likes 요청 시,")
    @Nested
    class GetUserLikes {

        @DisplayName("본인의 좋아요 목록 조회 시 200과 목록이 반환된다.")
        @Test
        void returnsLikes_whenRequestingOwnLikes() {
            HttpEntity<Void> req = new HttpEntity<>(authHeaders());
            testRestTemplate.exchange(likeUrl(savedProduct.getId()), HttpMethod.POST, req, new ParameterizedTypeReference<>() {});

            ResponseEntity<ApiResponse<List<LikeV1Dto.LikeResponse>>> response = testRestTemplate.exchange(
                userLikesUrl(testUser.getId()), HttpMethod.GET, req,
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data()).hasSize(1),
                () -> assertThat(response.getBody().data().get(0).productId()).isEqualTo(savedProduct.getId()),
                () -> assertThat(response.getBody().data().get(0).productName()).isEqualTo("나이키 에어맥스")
            );
        }

        @DisplayName("삭제된 상품의 좋아요는 목록에서 제외된다.")
        @Test
        void excludesDeletedProducts_fromLikeList() {
            HttpEntity<Void> req = new HttpEntity<>(authHeaders());
            testRestTemplate.exchange(likeUrl(savedProduct.getId()), HttpMethod.POST, req, new ParameterizedTypeReference<>() {});
            savedProduct.delete();
            productJpaRepository.save(savedProduct);

            ResponseEntity<ApiResponse<List<LikeV1Dto.LikeResponse>>> response = testRestTemplate.exchange(
                userLikesUrl(testUser.getId()), HttpMethod.GET, req,
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data()).isEmpty()
            );
        }

        @DisplayName("타인의 좋아요 목록 조회 시 404가 반환된다.")
        @Test
        void returns404_whenRequestingOtherUserLikes() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                userLikesUrl(testUser.getId() + 1), HttpMethod.GET,
                new HttpEntity<>(authHeaders()), new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
