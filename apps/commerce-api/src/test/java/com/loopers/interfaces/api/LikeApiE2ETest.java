package com.loopers.interfaces.api;

import com.loopers.application.user.UserService;
import com.loopers.domain.product.ProductLikeViewModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.product.ProductLikeViewJpaRepository;
import com.loopers.interfaces.api.like.LikeDto;
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
import org.springframework.http.*;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeApiE2ETest {

    private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    private static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private UserService userService;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private ProductLikeViewJpaRepository productLikeViewJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private UserModel savedUser;
    private ProductModel savedProduct;
    private HttpHeaders userHeaders;

    @BeforeEach
    void setUp() {
        savedUser = userService.signUp(new UserModel(
            "user01", "Password1!", "홍길동",
            LocalDate.of(1990, 1, 1), "user@example.com"
        ));
        savedProduct = productJpaRepository.save(new ProductModel("에어포스1", 139000L, 1L));
        productLikeViewJpaRepository.save(new ProductLikeViewModel(savedProduct.getId()));

        userHeaders = new HttpHeaders();
        userHeaders.set(LOGIN_ID_HEADER, "user01");
        userHeaders.set(LOGIN_PW_HEADER, "Password1!");
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/products/{productId}/likes")
    @Nested
    class PostLike {

        @DisplayName("정상 좋아요 등록 시, 200 OK를 반환한다.")
        @Test
        void returns200_whenLikeSucceeds() {
            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                "/api/v1/products/" + savedProduct.getId() + "/likes",
                HttpMethod.POST,
                new HttpEntity<>(userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("비로그인 상태로 요청하면, 401을 반환한다.")
        @Test
        void returns401_whenNotLoggedIn() {
            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                "/api/v1/products/" + savedProduct.getId() + "/likes",
                HttpMethod.POST,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("존재하지 않는 상품에 좋아요하면, 404를 반환한다.")
        @Test
        void returns404_whenProductNotFound() {
            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                "/api/v1/products/999/likes",
                HttpMethod.POST,
                new HttpEntity<>(userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("이미 좋아요한 상품에 재등록 시, 200 OK를 반환한다. (멱등)")
        @Test
        void returns200_whenAlreadyLiked() {
            // arrange
            testRestTemplate.exchange(
                "/api/v1/products/" + savedProduct.getId() + "/likes",
                HttpMethod.POST,
                new HttpEntity<>(userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                "/api/v1/products/" + savedProduct.getId() + "/likes",
                HttpMethod.POST,
                new HttpEntity<>(userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    @Nested
    class DeleteLike {

        @DisplayName("정상 좋아요 취소 시, 200 OK를 반환한다.")
        @Test
        void returns200_whenUnlikeSucceeds() {
            // arrange
            testRestTemplate.exchange(
                "/api/v1/products/" + savedProduct.getId() + "/likes",
                HttpMethod.POST,
                new HttpEntity<>(userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                "/api/v1/products/" + savedProduct.getId() + "/likes",
                HttpMethod.DELETE,
                new HttpEntity<>(userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("좋아요하지 않은 상품을 취소하면, 404를 반환한다.")
        @Test
        void returns404_whenNotLiked() {
            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                "/api/v1/products/" + savedProduct.getId() + "/likes",
                HttpMethod.DELETE,
                new HttpEntity<>(userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/users/{userId}/likes")
    @Nested
    class GetLikes {

        @DisplayName("내 좋아요 목록 조회 시, 상품 정보가 포함된 목록을 반환한다.")
        @Test
        void returns200_withLikeList() {
            // arrange
            testRestTemplate.exchange(
                "/api/v1/products/" + savedProduct.getId() + "/likes",
                HttpMethod.POST,
                new HttpEntity<>(userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // act
            ResponseEntity<ApiResponse<List<LikeDto.LikeResponse>>> response = testRestTemplate.exchange(
                "/api/v1/users/" + savedUser.getId() + "/likes",
                HttpMethod.GET,
                new HttpEntity<>(userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data()).hasSize(1);
            assertThat(response.getBody().data().get(0).productName()).isEqualTo("에어포스1");
        }

        @DisplayName("다른 회원의 좋아요 목록을 조회하면, 403을 반환한다.")
        @Test
        void returns403_whenAccessingOtherUserLikes() {
            // arrange
            userService.signUp(new UserModel(
                "user02", "Password2@", "김철수",
                LocalDate.of(1995, 5, 5), "other@example.com"
            ));
            HttpHeaders otherHeaders = new HttpHeaders();
            otherHeaders.set(LOGIN_ID_HEADER, "user02");
            otherHeaders.set(LOGIN_PW_HEADER, "Password2@");

            // act
            ResponseEntity<ApiResponse<List<LikeDto.LikeResponse>>> response = testRestTemplate.exchange(
                "/api/v1/users/" + savedUser.getId() + "/likes",
                HttpMethod.GET,
                new HttpEntity<>(otherHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @DisplayName("비로그인 상태로 요청하면, 401을 반환한다.")
        @Test
        void returns401_whenNotLoggedIn() {
            // act
            ResponseEntity<ApiResponse<List<LikeDto.LikeResponse>>> response = testRestTemplate.exchange(
                "/api/v1/users/" + savedUser.getId() + "/likes",
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
