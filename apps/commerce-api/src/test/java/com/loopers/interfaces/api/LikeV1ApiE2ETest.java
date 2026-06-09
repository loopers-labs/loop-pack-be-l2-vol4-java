package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
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

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.user.PasswordEncrypter;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest {

    private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    private static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";
    private static final ParameterizedTypeReference<ApiResponse<Map<String, Object>>> MAP_RESPONSE = new ParameterizedTypeReference<>() {};

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private UserJpaRepository userJpaRepository;

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

    private UserModel saveUser(String loginId, String rawPassword) {
        UserModel user = UserModel.builder()
            .rawLoginId(loginId)
            .rawPassword(rawPassword)
            .rawName("테스트유저")
            .rawBirthDate(LocalDate.of(1995, 3, 21))
            .rawEmail(loginId + "@example.com")
            .passwordEncrypter(passwordEncrypter)
            .build();

        return userJpaRepository.save(user);
    }

    private ProductModel saveProduct(String name) {
        ProductModel product = ProductModel.builder()
            .brandId(1L)
            .rawName(name)
            .rawDescription("설명")
            .rawPrice(39_000)
            .rawStock(50)
            .build();

        return productJpaRepository.save(product);
    }

    private HttpEntity<Void> memberRequest(String loginId, String rawPassword) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(LOGIN_ID_HEADER, loginId);
        headers.add(LOGIN_PW_HEADER, rawPassword);

        return new HttpEntity<>(headers);
    }

    private HttpEntity<Void> unauthenticatedRequest() {
        return new HttpEntity<>(new HttpHeaders());
    }

    private String likesEndpoint(Long productId) {
        return String.format("/api/v1/products/%d/likes", productId);
    }

    @DisplayName("좋아요 등록 - POST /api/v1/products/{productId}/likes")
    @Nested
    class CreateLike {

        private static final String RAW_PASSWORD = "Kyle!2030";

        @DisplayName("정상 요청이면, 200 OK와 함께 좋아요가 저장된다.")
        @Test
        void returnsOk_andSavesLike_whenRequestIsValid() {
            // arrange
            UserModel user = saveUser("testuser1", RAW_PASSWORD);
            ProductModel product = saveProduct("감성 가디건");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                likesEndpoint(product.getId()),
                HttpMethod.POST,
                memberRequest(user.getLoginId().value(), RAW_PASSWORD),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(likeJpaRepository.existsByUserIdAndProductId(user.getId(), product.getId())).isTrue()
            );
        }

        @DisplayName("이미 좋아요한 상품에 다시 등록해도, 200 OK로 마무리되고 행은 한 건이다(멱등).")
        @Test
        void returnsOk_andKeepsSingleRow_whenLikedAgain() {
            // arrange
            UserModel user = saveUser("testuser1", RAW_PASSWORD);
            ProductModel product = saveProduct("감성 가디건");
            String endpoint = likesEndpoint(product.getId());
            HttpEntity<Void> request = memberRequest(user.getLoginId().value(), RAW_PASSWORD);

            // act
            testRestTemplate.exchange(endpoint, HttpMethod.POST, request, MAP_RESPONSE);
            ResponseEntity<ApiResponse<Map<String, Object>>> secondResponse =
                testRestTemplate.exchange(endpoint, HttpMethod.POST, request, MAP_RESPONSE);

            // assert
            assertAll(
                () -> assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(secondResponse.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(likeJpaRepository.count()).isEqualTo(1L)
            );
        }

        @DisplayName("회원 인증 헤더가 없으면, 401 Unauthorized로 거절된다.")
        @Test
        void returnsUnauthenticated_whenAuthHeaderIsMissing() {
            // arrange
            ProductModel product = saveProduct("감성 가디건");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                likesEndpoint(product.getId()),
                HttpMethod.POST,
                unauthenticatedRequest(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.UNAUTHENTICATED.getCode()),
                () -> assertThat(likeJpaRepository.count()).isEqualTo(0L)
            );
        }

        @DisplayName("대상 상품이 존재하지 않으면, 404 Not Found로 거절된다.")
        @Test
        void returnsNotFound_whenProductIsAbsent() {
            // arrange
            UserModel user = saveUser("testuser1", RAW_PASSWORD);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                likesEndpoint(99999L),
                HttpMethod.POST,
                memberRequest(user.getLoginId().value(), RAW_PASSWORD),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.NOT_FOUND.getCode()),
                () -> assertThat(likeJpaRepository.count()).isEqualTo(0L)
            );
        }
    }

    @DisplayName("좋아요 취소 - DELETE /api/v1/products/{productId}/likes")
    @Nested
    class DeleteLike {

        private static final String RAW_PASSWORD = "Kyle!2030";

        @DisplayName("정상 요청이면, 200 OK로 처리되고 좋아요 행이 제거된다.")
        @Test
        void returnsOk_andRemovesLike_whenRequestIsValid() {
            // arrange
            UserModel user = saveUser("testuser1", RAW_PASSWORD);
            ProductModel product = saveProduct("감성 가디건");
            likeJpaRepository.save(LikeModel.builder()
                .userId(user.getId())
                .productId(product.getId())
                .build());

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                likesEndpoint(product.getId()),
                HttpMethod.DELETE,
                memberRequest(user.getLoginId().value(), RAW_PASSWORD),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(likeJpaRepository.existsByUserIdAndProductId(user.getId(), product.getId())).isFalse()
            );
        }

        @DisplayName("좋아요 기록이 없어도, 200 OK로 마무리된다(멱등).")
        @Test
        void returnsOk_whenLikeIsAbsent() {
            // arrange
            UserModel user = saveUser("testuser1", RAW_PASSWORD);
            ProductModel product = saveProduct("감성 가디건");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                likesEndpoint(product.getId()),
                HttpMethod.DELETE,
                memberRequest(user.getLoginId().value(), RAW_PASSWORD),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)
            );
        }

        @DisplayName("동일 상품에 취소를 두 번 요청해도, 두 응답 모두 200 OK로 마무리된다(멱등).")
        @Test
        void returnsOk_whenCancelledTwice() {
            // arrange
            UserModel user = saveUser("testuser1", RAW_PASSWORD);
            ProductModel product = saveProduct("감성 가디건");
            String endpoint = likesEndpoint(product.getId());
            HttpEntity<Void> request = memberRequest(user.getLoginId().value(), RAW_PASSWORD);
            likeJpaRepository.save(LikeModel.builder()
                .userId(user.getId())
                .productId(product.getId())
                .build());

            // act
            testRestTemplate.exchange(endpoint, HttpMethod.DELETE, request, MAP_RESPONSE);
            ResponseEntity<ApiResponse<Map<String, Object>>> secondResponse =
                testRestTemplate.exchange(endpoint, HttpMethod.DELETE, request, MAP_RESPONSE);

            // assert
            assertAll(
                () -> assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(secondResponse.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)
            );
        }

        @DisplayName("다른 회원이 같은 상품을 좋아요한 경우, 취소해도 다른 회원의 좋아요는 유지된다.")
        @Test
        void keepsOtherUserLike_whenOneUserCancels() {
            // arrange
            UserModel user1 = saveUser("testuser1", RAW_PASSWORD);
            UserModel user2 = saveUser("testuser2", RAW_PASSWORD);
            ProductModel product = saveProduct("감성 가디건");
            likeJpaRepository.save(LikeModel.builder()
                .userId(user1.getId())
                .productId(product.getId())
                .build());
            likeJpaRepository.save(LikeModel.builder()
                .userId(user2.getId())
                .productId(product.getId())
                .build());

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                likesEndpoint(product.getId()), HttpMethod.DELETE,
                memberRequest(user1.getLoginId().value(), RAW_PASSWORD), MAP_RESPONSE);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(likeJpaRepository.existsByUserIdAndProductId(user1.getId(), product.getId())).isFalse(),
                () -> assertThat(likeJpaRepository.existsByUserIdAndProductId(user2.getId(), product.getId())).isTrue()
            );
        }

        @DisplayName("회원 인증 헤더가 없으면, 401 Unauthorized로 거절된다.")
        @Test
        void returnsUnauthenticated_whenAuthHeaderIsMissing() {
            // arrange
            ProductModel product = saveProduct("감성 가디건");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                likesEndpoint(product.getId()),
                HttpMethod.DELETE,
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

        @DisplayName("대상 상품이 존재하지 않으면, 404 Not Found로 거절된다.")
        @Test
        void returnsNotFound_whenProductIsAbsent() {
            // arrange
            UserModel user = saveUser("testuser1", RAW_PASSWORD);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                likesEndpoint(99999L),
                HttpMethod.DELETE,
                memberRequest(user.getLoginId().value(), RAW_PASSWORD),
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
}
