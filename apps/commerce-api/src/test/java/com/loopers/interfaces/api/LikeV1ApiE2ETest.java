package com.loopers.interfaces.api;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.like.LikeFacade;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.user.UserCommand;
import com.loopers.application.user.UserFacade;
import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.like.LikeV1Dto;
import com.loopers.interfaces.auth.AuthHeaders;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/me/likes";
    private static final String LOGIN_ID = "user01";
    private static final String LOGIN_PW = "Abcd1234!";

    private final TestRestTemplate testRestTemplate;
    private final BrandFacade brandFacade;
    private final ProductFacade productFacade;
    private final LikeFacade likeFacade;
    private final UserFacade userFacade;
    private final ProductJpaRepository productJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    private Long meId;
    private Long brandId;
    private Long firstLikedProductId;
    private Long secondLikedProductId;

    @Autowired
    public LikeV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandFacade brandFacade,
        ProductFacade productFacade,
        LikeFacade likeFacade,
        UserFacade userFacade,
        ProductJpaRepository productJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandFacade = brandFacade;
        this.productFacade = productFacade;
        this.likeFacade = likeFacade;
        this.userFacade = userFacade;
        this.productJpaRepository = productJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        meId = userFacade.signUp(new UserCommand.SignUp(
            LOGIN_ID,
            LOGIN_PW,
            "김철수",
            LocalDate.of(1999, 3, 22),
            "user@example.com"
        )).id();
        brandId = brandFacade.create("나이키", "Just Do It").id();
        firstLikedProductId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId).id();
        secondLikedProductId = productFacade.createProduct("페가수스 40", "쿠셔닝", 139_000L, 30, brandId).id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AuthHeaders.LOGIN_ID, LOGIN_ID);
        headers.set(AuthHeaders.LOGIN_PW, LOGIN_PW);
        return headers;
    }

    @DisplayName("GET /api/v1/me/likes")
    @Nested
    class GetMyLikes {

        @DisplayName("인증 헤더와 함께 요청하면, 200 과 본인이 좋아요한 상품 목록을 최신순으로 반환한다.")
        @Test
        void returnsMyLikedProducts_orderedByLikedAtDesc_whenAuthenticated() {
            // given
            likeFacade.like(meId, firstLikedProductId);
            likeFacade.like(meId, secondLikedProductId);

            // when
            ParameterizedTypeReference<ApiResponse<List<LikeV1Dto.LikedProductResponse>>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<LikeV1Dto.LikedProductResponse>>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.GET, new HttpEntity<>(authHeaders()), responseType
            );

            // then
            List<LikeV1Dto.LikedProductResponse> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(data).hasSize(2),
                () -> assertThat(data.get(0).id()).isEqualTo(secondLikedProductId),
                () -> assertThat(data.get(0).name()).isEqualTo("페가수스 40"),
                () -> assertThat(data.get(0).brand().id()).isEqualTo(brandId),
                () -> assertThat(data.get(0).brand().name()).isEqualTo("나이키"),
                () -> assertThat(data.get(1).id()).isEqualTo(firstLikedProductId),
                () -> assertThat(data.get(1).name()).isEqualTo("에어맥스 270")
            );
        }

        @DisplayName("좋아요한 적이 없으면, 200 과 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoLikes() {
            // given (좋아요 없음)

            // when
            ParameterizedTypeReference<ApiResponse<List<LikeV1Dto.LikedProductResponse>>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<LikeV1Dto.LikedProductResponse>>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.GET, new HttpEntity<>(authHeaders()), responseType
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data()).isEmpty()
            );
        }

        @DisplayName("좋아요한 상품이 soft-delete 되면 목록에서 제외된다.")
        @Test
        void excludesSoftDeletedProduct_fromResponse() {
            // given
            likeFacade.like(meId, firstLikedProductId);
            likeFacade.like(meId, secondLikedProductId);
            softDelete(firstLikedProductId);

            // when
            ParameterizedTypeReference<ApiResponse<List<LikeV1Dto.LikedProductResponse>>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<LikeV1Dto.LikedProductResponse>>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.GET, new HttpEntity<>(authHeaders()), responseType
            );

            // then
            List<LikeV1Dto.LikedProductResponse> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data).hasSize(1),
                () -> assertThat(data.get(0).id()).isEqualTo(secondLikedProductId)
            );
        }

        @DisplayName("인증 헤더가 누락되면, UNAUTHORIZED 를 반환한다.")
        @Test
        void returnsUnauthorized_whenAuthHeadersAreMissing() {
            // given
            likeFacade.like(meId, firstLikedProductId);

            // when
            ParameterizedTypeReference<ApiResponse<List<LikeV1Dto.LikedProductResponse>>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<LikeV1Dto.LikedProductResponse>>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), responseType
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @DisplayName("page 와 size 쿼리 파라미터로 페이지를 슬라이싱한다.")
        @Test
        void slicesResponse_whenPageAndSizeAreSpecified() {
            // given
            Long thirdLikedProductId = productFacade.createProduct("줌 보메로 5", "라이프스타일", 189_000L, 10, brandId).id();
            likeFacade.like(meId, firstLikedProductId);
            likeFacade.like(meId, secondLikedProductId);
            likeFacade.like(meId, thirdLikedProductId);

            // when
            ParameterizedTypeReference<ApiResponse<List<LikeV1Dto.LikedProductResponse>>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<LikeV1Dto.LikedProductResponse>>> firstPage = testRestTemplate.exchange(
                ENDPOINT + "?page=0&size=2", HttpMethod.GET, new HttpEntity<>(authHeaders()), responseType
            );
            ResponseEntity<ApiResponse<List<LikeV1Dto.LikedProductResponse>>> secondPage = testRestTemplate.exchange(
                ENDPOINT + "?page=1&size=2", HttpMethod.GET, new HttpEntity<>(authHeaders()), responseType
            );

            // then
            assertAll(
                () -> assertThat(firstPage.getBody().data()).hasSize(2),
                () -> assertThat(firstPage.getBody().data().get(0).id()).isEqualTo(thirdLikedProductId),
                () -> assertThat(firstPage.getBody().data().get(1).id()).isEqualTo(secondLikedProductId),
                () -> assertThat(secondPage.getBody().data()).hasSize(1),
                () -> assertThat(secondPage.getBody().data().get(0).id()).isEqualTo(firstLikedProductId)
            );
        }
    }

    private void softDelete(Long productId) {
        ProductModel product = productJpaRepository.findById(productId).orElseThrow();
        product.delete();
        productJpaRepository.save(product);
    }
}
