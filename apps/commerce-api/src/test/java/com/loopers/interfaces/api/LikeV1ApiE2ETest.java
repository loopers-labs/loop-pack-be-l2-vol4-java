package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.user.BirthDate;
import com.loopers.domain.user.Email;
import com.loopers.domain.user.EncodedPassword;
import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.Name;
import com.loopers.domain.user.PasswordEncoder;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest {

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final PasswordEncoder passwordEncoder;
    private final DatabaseCleanUp databaseCleanUp;

    private Long productId;
    private Long user1Id;

    @Autowired
    public LikeV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            BrandJpaRepository brandJpaRepository,
            ProductJpaRepository productJpaRepository,
            UserJpaRepository userJpaRepository,
            PasswordEncoder passwordEncoder,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.userJpaRepository = userJpaRepository;
        this.passwordEncoder = passwordEncoder;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        Long brandId = brandJpaRepository.save(Brand.create("브랜드A", "소개")).getId();
        productId = productJpaRepository.save(
                Product.create(brandId, "상품1", Money.of(1_000L))).getId();
        user1Id = createUser("user1");
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private static final String PASSWORD = "Abcd123!";

    private Long createUser(String loginId) {
        UserModel user = new UserModel(
                new LoginId(loginId),
                new Name("유저"),
                new BirthDate(LocalDate.of(1999, 1, 1)),
                new Email(loginId + "@loopers.com"),
                EncodedPassword.create(passwordEncoder, PASSWORD)
        );
        return userJpaRepository.save(user).getId();
    }

    private static HttpHeaders loginHeader(String loginId) {
        HttpHeaders headers = new HttpHeaders();
        if (loginId != null) {
            headers.add("X-Loopers-LoginId", loginId);
            headers.add("X-Loopers-LoginPw", PASSWORD);
        }
        return headers;
    }

    private ResponseEntity<ApiResponse<Void>> register(String loginId, Long productId) {
        ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange("/api/v1/products/" + productId + "/likes", HttpMethod.POST,
                new HttpEntity<>(loginHeader(loginId)), type);
    }

    private ResponseEntity<ApiResponse<Void>> cancel(String loginId, Long productId) {
        ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange("/api/v1/products/" + productId + "/likes", HttpMethod.DELETE,
                new HttpEntity<>(loginHeader(loginId)), type);
    }

    private ResponseEntity<ApiResponse<LikeV1Dto.LikePageResponse>> getLikes(String loginId, Long userId) {
        ParameterizedTypeReference<ApiResponse<LikeV1Dto.LikePageResponse>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange("/api/v1/users/" + userId + "/likes", HttpMethod.GET,
                new HttpEntity<>(loginHeader(loginId)), type);
    }

    @DisplayName("POST /api/v1/products/{productId}/likes")
    @Nested
    class Register {

        @DisplayName("좋아요하지 않은 상품에 좋아요하면 200 이고 내 목록에 추가된다. (US-04, AC-04-1)")
        @Test
        void registersLike() {
            ResponseEntity<ApiResponse<Void>> response = register("user1", productId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(getLikes("user1", user1Id).getBody().data().content()).hasSize(1);
        }

        @DisplayName("이미 좋아요한 상품에 다시 요청해도 200 이고 1개만 유지된다. (AC-04-2 멱등)")
        @Test
        void isIdempotent_whenAlreadyLiked() {
            register("user1", productId);
            ResponseEntity<ApiResponse<Void>> response = register("user1", productId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(getLikes("user1", user1Id).getBody().data().content()).hasSize(1);
        }

        @DisplayName("존재하지 않는 상품에 좋아요하면 404 NOT_FOUND. (AC-04-3)")
        @Test
        void returnsNotFound_whenProductMissing() {
            ResponseEntity<ApiResponse<Void>> response = register("user1", 99999L);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("로그인 헤더가 누락되면 400 BAD_REQUEST. (AC-04-4)")
        @Test
        void returnsBadRequest_whenHeaderMissing() {
            ResponseEntity<ApiResponse<Void>> response = register(null, productId);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("식별할 수 없는 LoginId 면 401 UNAUTHORIZED.")
        @Test
        void returnsUnauthorized_whenUserUnknown() {
            ResponseEntity<ApiResponse<Void>> response = register("ghost", productId);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    @Nested
    class Cancel {

        @DisplayName("좋아요한 상품을 취소하면 200 이고 내 목록에서 제거된다. (US-05, AC-05-1)")
        @Test
        void cancelsLike() {
            register("user1", productId);

            ResponseEntity<ApiResponse<Void>> response = cancel("user1", productId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(getLikes("user1", user1Id).getBody().data().content()).isEmpty();
        }

        @DisplayName("좋아요하지 않은 상품을 취소해도 200 으로 처리한다. (AC-05-2 멱등)")
        @Test
        void isIdempotent_whenNotLiked() {
            ResponseEntity<ApiResponse<Void>> response = cancel("user1", productId);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("존재하지 않는 상품을 취소해도 200 으로 처리한다. (AC-05-4 멱등)")
        @Test
        void isIdempotent_whenProductMissing() {
            ResponseEntity<ApiResponse<Void>> response = cancel("user1", 99999L);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @DisplayName("GET /api/v1/users/{userId}/likes")
    @Nested
    class GetMyLikes {

        @DisplayName("본인이 좋아요한 상품만 페이징하여 돌려준다. (US-06, AC-06-1)")
        @Test
        void returnsOnlyOwnLikes() {
            Long user2Id = createUser("user2");
            register("user1", productId);
            register("user2", productId);

            ResponseEntity<ApiResponse<LikeV1Dto.LikePageResponse>> response = getLikes("user1", user1Id);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().content()).hasSize(1);
            assertThat(response.getBody().data().content()).allMatch(l -> l.userId().equals(user1Id));
            assertThat(user2Id).isNotEqualTo(user1Id);
        }

        @DisplayName("다른 사용자의 좋아요 목록을 요청하면 403 FORBIDDEN. (AC-06-2)")
        @Test
        void returnsForbidden_whenNotOwner() {
            Long user2Id = createUser("user2");

            ResponseEntity<ApiResponse<LikeV1Dto.LikePageResponse>> response = getLikes("user1", user2Id);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @DisplayName("로그인 헤더가 누락되면 400 BAD_REQUEST.")
        @Test
        void returnsBadRequest_whenHeaderMissing() {
            ResponseEntity<ApiResponse<LikeV1Dto.LikePageResponse>> response = getLikes(null, user1Id);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
