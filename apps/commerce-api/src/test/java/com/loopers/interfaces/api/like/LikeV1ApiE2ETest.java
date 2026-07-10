package com.loopers.interfaces.api.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStatsModel;
import com.loopers.domain.product.ProductStatsRepository;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.application.outbox.OutboxRelay;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.AuthHeaders;
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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest {

    private static final String BASE_URL = "/api/v1/products";
    private static final String LOGIN_ID = "user01";
    private static final String LOGIN_PW = "Password1!";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductStatsRepository productStatsRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private PasswordEncryptor passwordEncryptor;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpEntity<Void> authHeaderEntity(String loginId, String loginPw) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AuthHeaders.LOGIN_ID, loginId);
        headers.set(AuthHeaders.LOGIN_PW, loginPw);
        return new HttpEntity<>(null, headers);
    }

    @DisplayName("POST /api/v1/products/{productId}/likes")
    @Nested
    class Like {

        @DisplayName("유효한 인증 정보와 상품 ID로 좋아요를 누르면 성공 응답을 반환하고 상품의 좋아요 수가 1 증가한다")
        @Test
        void returnsSuccess_whenLikeIsApplied() {
            // given
            userRepository.save(new UserModel(
                LOGIN_ID, LOGIN_PW, "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor
            ));
            BrandModel brand = brandRepository.save(new BrandModel("테스트브랜드"));
            ProductModel product = productRepository.save(new ProductModel(brand.getId(), "테스트상품", BigDecimal.valueOf(5000)));
            productStatsRepository.save(new ProductStatsModel(product));

            // when
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL + "/" + product.getId() + "/likes",
                HttpMethod.POST,
                authHeaderEntity(LOGIN_ID, LOGIN_PW),
                responseType
            );
            outboxRelay.relay();

            // then
            ProductStatsModel persistedStats = productStatsRepository.findByProduct(product).orElseThrow();
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(persistedStats.getLikeCount()).isEqualTo(1L)
            );
        }

        @DisplayName("이미 좋아요한 상품에 다시 좋아요를 누르면 멱등하게 처리되어 좋아요 수가 중복 증가하지 않는다")
        @Test
        void doesNotDoubleCount_whenAlreadyLiked() {
            // given
            userRepository.save(new UserModel(
                LOGIN_ID, LOGIN_PW, "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor
            ));
            BrandModel brand = brandRepository.save(new BrandModel("테스트브랜드"));
            ProductModel product = productRepository.save(new ProductModel(brand.getId(), "테스트상품", BigDecimal.valueOf(5000)));
            productStatsRepository.save(new ProductStatsModel(product));
            testRestTemplate.exchange(
                BASE_URL + "/" + product.getId() + "/likes",
                HttpMethod.POST,
                authHeaderEntity(LOGIN_ID, LOGIN_PW),
                Void.class
            );
            outboxRelay.relay();

            // when
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL + "/" + product.getId() + "/likes",
                HttpMethod.POST,
                authHeaderEntity(LOGIN_ID, LOGIN_PW),
                responseType
            );
            outboxRelay.relay();

            // then
            ProductStatsModel persistedStats = productStatsRepository.findByProduct(product).orElseThrow();
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(persistedStats.getLikeCount()).isEqualTo(1L)
            );
        }

        @DisplayName("존재하지 않는 상품 ID로 좋아요하면 404 Not Found를 반환한다")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            // given
            userRepository.save(new UserModel(
                LOGIN_ID, LOGIN_PW, "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor
            ));

            // when
            ResponseEntity<Void> response = testRestTemplate.exchange(
                BASE_URL + "/999/likes",
                HttpMethod.POST,
                authHeaderEntity(LOGIN_ID, LOGIN_PW),
                Void.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("LOGIN_ID 헤더가 없으면 400 Bad Request를 반환한다")
        @Test
        void returnsBadRequest_whenLoginIdHeaderIsMissing() {
            // when
            HttpHeaders headers = new HttpHeaders();
            headers.set(AuthHeaders.LOGIN_PW, LOGIN_PW);
            ResponseEntity<Void> response = testRestTemplate.exchange(
                BASE_URL + "/1/likes",
                HttpMethod.POST,
                new HttpEntity<>(null, headers),
                Void.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("LOGIN_PW 헤더가 없으면 400 Bad Request를 반환한다")
        @Test
        void returnsBadRequest_whenLoginPwHeaderIsMissing() {
            // when
            HttpHeaders headers = new HttpHeaders();
            headers.set(AuthHeaders.LOGIN_ID, LOGIN_ID);
            ResponseEntity<Void> response = testRestTemplate.exchange(
                BASE_URL + "/1/likes",
                HttpMethod.POST,
                new HttpEntity<>(null, headers),
                Void.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 틀리면 400 Bad Request를 반환한다")
        @Test
        void returnsBadRequest_whenPasswordIsWrong() {
            // given
            userRepository.save(new UserModel(
                LOGIN_ID, LOGIN_PW, "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor
            ));
            BrandModel brand = brandRepository.save(new BrandModel("테스트브랜드"));
            ProductModel product = productRepository.save(new ProductModel(brand.getId(), "테스트상품", BigDecimal.valueOf(5000)));

            // when
            ResponseEntity<Void> response = testRestTemplate.exchange(
                BASE_URL + "/" + product.getId() + "/likes",
                HttpMethod.POST,
                authHeaderEntity(LOGIN_ID, "WrongPass1!"),
                Void.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    @Nested
    class Unlike {

        @DisplayName("좋아요한 상품에 좋아요 취소를 누르면 성공 응답을 반환하고 상품의 좋아요 수가 1 감소한다")
        @Test
        void returnsSuccess_whenUnlikeIsApplied() {
            // given
            userRepository.save(new UserModel(
                LOGIN_ID, LOGIN_PW, "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor
            ));
            BrandModel brand = brandRepository.save(new BrandModel("테스트브랜드"));
            ProductModel product = productRepository.save(new ProductModel(brand.getId(), "테스트상품", BigDecimal.valueOf(5000)));
            productStatsRepository.save(new ProductStatsModel(product));
            testRestTemplate.exchange(
                BASE_URL + "/" + product.getId() + "/likes",
                HttpMethod.POST,
                authHeaderEntity(LOGIN_ID, LOGIN_PW),
                Void.class
            );
            outboxRelay.relay();

            // when
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL + "/" + product.getId() + "/likes",
                HttpMethod.DELETE,
                authHeaderEntity(LOGIN_ID, LOGIN_PW),
                responseType
            );
            outboxRelay.relay();

            // then
            ProductStatsModel persistedStats = productStatsRepository.findByProduct(product).orElseThrow();
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(persistedStats.getLikeCount()).isEqualTo(0L)
            );
        }

        @DisplayName("좋아요하지 않은 상품에 좋아요 취소를 누르면 멱등하게 처리되어 성공 응답을 반환한다")
        @Test
        void returnsSuccess_whenNotLiked() {
            // given
            userRepository.save(new UserModel(
                LOGIN_ID, LOGIN_PW, "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor
            ));
            BrandModel brand = brandRepository.save(new BrandModel("테스트브랜드"));
            ProductModel product = productRepository.save(new ProductModel(brand.getId(), "테스트상품", BigDecimal.valueOf(5000)));
            productStatsRepository.save(new ProductStatsModel(product));

            // when
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL + "/" + product.getId() + "/likes",
                HttpMethod.DELETE,
                authHeaderEntity(LOGIN_ID, LOGIN_PW),
                responseType
            );

            // then
            ProductStatsModel persistedStats = productStatsRepository.findByProduct(product).orElseThrow();
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(persistedStats.getLikeCount()).isEqualTo(0L)
            );
        }

        @DisplayName("존재하지 않는 상품 ID로 좋아요 취소하면 404 Not Found를 반환한다")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            // given
            userRepository.save(new UserModel(
                LOGIN_ID, LOGIN_PW, "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor
            ));

            // when
            ResponseEntity<Void> response = testRestTemplate.exchange(
                BASE_URL + "/999/likes",
                HttpMethod.DELETE,
                authHeaderEntity(LOGIN_ID, LOGIN_PW),
                Void.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("LOGIN_ID 헤더가 없으면 400 Bad Request를 반환한다")
        @Test
        void returnsBadRequest_whenLoginIdHeaderIsMissing() {
            // when
            HttpHeaders headers = new HttpHeaders();
            headers.set(AuthHeaders.LOGIN_PW, LOGIN_PW);
            ResponseEntity<Void> response = testRestTemplate.exchange(
                BASE_URL + "/1/likes",
                HttpMethod.DELETE,
                new HttpEntity<>(null, headers),
                Void.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("LOGIN_PW 헤더가 없으면 400 Bad Request를 반환한다")
        @Test
        void returnsBadRequest_whenLoginPwHeaderIsMissing() {
            // when
            HttpHeaders headers = new HttpHeaders();
            headers.set(AuthHeaders.LOGIN_ID, LOGIN_ID);
            ResponseEntity<Void> response = testRestTemplate.exchange(
                BASE_URL + "/1/likes",
                HttpMethod.DELETE,
                new HttpEntity<>(null, headers),
                Void.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 틀리면 400 Bad Request를 반환한다")
        @Test
        void returnsBadRequest_whenPasswordIsWrong() {
            // given
            userRepository.save(new UserModel(
                LOGIN_ID, LOGIN_PW, "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor
            ));
            BrandModel brand = brandRepository.save(new BrandModel("테스트브랜드"));
            ProductModel product = productRepository.save(new ProductModel(brand.getId(), "테스트상품", BigDecimal.valueOf(5000)));

            // when
            ResponseEntity<Void> response = testRestTemplate.exchange(
                BASE_URL + "/" + product.getId() + "/likes",
                HttpMethod.DELETE,
                authHeaderEntity(LOGIN_ID, "WrongPass1!"),
                Void.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
