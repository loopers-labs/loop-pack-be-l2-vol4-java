package com.loopers.interfaces.api.wishlist;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.vo.ProductName;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.domain.user.enums.UserRole;
import com.loopers.domain.user.vo.BirthDay;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.Name;
import com.loopers.domain.user.vo.Password;
import com.loopers.domain.user.vo.UserId;
import com.loopers.interfaces.api.ApiResponse;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WishlistV1ApiE2ETest {

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final String DEFAULT_USERID = "wishUser1";
    private static final String DEFAULT_PASSWORD = "Dlaxodid1!";

    private UserModel savedUser;
    private ProductModel savedProduct;

    @BeforeEach
    void setUp() {
        savedUser = userRepository.save(new UserModel(
                new UserId(DEFAULT_USERID),
                new Password(passwordEncoder.encode(DEFAULT_PASSWORD)),
                new Name("찜유저"),
                new BirthDay("1990-01-01"),
                new Email("wish@test.com"),
                UserRole.USER
        ));
        BrandModel brand = brandRepository.save(new BrandModel("테스트브랜드"));
        savedProduct = productRepository.save(new ProductModel(brand.getId(), new ProductName("테스트상품")));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Loopers-LoginId", DEFAULT_USERID);
        headers.set("X-Loopers-LoginPw", DEFAULT_PASSWORD);
        return headers;
    }

    private void addLike() {
        testRestTemplate.exchange(
                "/api/v1/products/" + savedProduct.getId() + "/likes",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
        );
    }

    @DisplayName("POST /api/v1/products/{productId}/likes")
    @Nested
    class AddLike {

        @DisplayName("유효한 요청이면, 201 CREATED를 반환한다.")
        @Test
        void returnsCreated_whenRequestIsValid() {
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    "/api/v1/products/" + savedProduct.getId() + "/likes",
                    HttpMethod.POST, new HttpEntity<>(authHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @DisplayName("이미 찜한 상품이면, 409 CONFLICT를 반환한다.")
        @Test
        void returnsConflict_whenAlreadyLiked() {
            addLike();
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    "/api/v1/products/" + savedProduct.getId() + "/likes",
                    HttpMethod.POST, new HttpEntity<>(authHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("인증 정보가 없으면, 401 UNAUTHORIZED를 반환한다.")
        @Test
        void returnsUnauthorized_whenNoAuthHeader() {
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    "/api/v1/products/" + savedProduct.getId() + "/likes",
                    HttpMethod.POST, null, type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    @Nested
    class RemoveLike {

        @DisplayName("찜이 존재하면, 200 OK를 반환한다.")
        @Test
        void returnsOk_whenLikeExists() {
            addLike();
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    "/api/v1/products/" + savedProduct.getId() + "/likes",
                    HttpMethod.DELETE, new HttpEntity<>(authHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("찜이 존재하지 않으면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenLikeDoesNotExist() {
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    "/api/v1/products/" + savedProduct.getId() + "/likes",
                    HttpMethod.DELETE, new HttpEntity<>(authHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/users/{userId}/likes")
    @Nested
    class GetLikedProducts {

        @DisplayName("찜한 상품이 있으면, 200 OK와 목록을 반환한다.")
        @Test
        void returnsLikedProducts_whenUserHasLikes() {
            addLike();
            ParameterizedTypeReference<ApiResponse<List<WishlistV1Dto.LikedProductResponse>>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<List<WishlistV1Dto.LikedProductResponse>>> response = testRestTemplate.exchange(
                    "/api/v1/users/" + savedUser.getId() + "/likes",
                    HttpMethod.GET, new HttpEntity<>(authHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data()).hasSize(1);
            assertThat(response.getBody().data().get(0).id()).isEqualTo(savedProduct.getId());
        }

        @DisplayName("찜한 상품이 없으면, 200 OK와 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenUserHasNoLikes() {
            ParameterizedTypeReference<ApiResponse<List<WishlistV1Dto.LikedProductResponse>>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<List<WishlistV1Dto.LikedProductResponse>>> response = testRestTemplate.exchange(
                    "/api/v1/users/" + savedUser.getId() + "/likes",
                    HttpMethod.GET, new HttpEntity<>(authHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data()).isEmpty();
        }
    }
}
