package com.loopers.product.interfaces;

import com.loopers.brand.domain.BrandModel;
import com.loopers.brand.infrastructure.BrandJpaRepository;
import com.loopers.like.application.LikeFacade;
import com.loopers.product.domain.ProductModel;
import com.loopers.product.infrastructure.ProductJpaRepository;
import com.loopers.support.response.ApiResponse;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/products";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private LikeFacade likeFacade;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("GET /api/v1/products")
    @Nested
    class GetProducts {

        @DisplayName("정상 요청이면, 200 OK와 ProductSummaryResponse 목록을 반환한다.")
        @Test
        void returnsProductList_whenRequestIsValid() {
            // arrange
            productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, null));
            productJpaRepository.save(new ProductModel("조던1", "나이키 농구화", 200000L, null));

            // act
            ParameterizedTypeReference<ApiResponse<List<ProductV1Dto.ProductSummaryResponse>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<ProductV1Dto.ProductSummaryResponse>>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.GET, new HttpEntity<>(null), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data()).hasSize(2)
            );
        }

        @DisplayName("목록 응답에는 description 필드가 포함되지 않는다.")
        @Test
        void returnsListWithoutDescription_whenRequestIsValid() {
            // arrange
            productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화 상세 설명", 150000L, null));

            // act
            ParameterizedTypeReference<ApiResponse<List<Map<String, Object>>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<Map<String, Object>>>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.GET, new HttpEntity<>(null), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().get(0)).doesNotContainKey("description")
            );
        }

        @DisplayName("brandId 쿼리 파라미터를 사용하면 해당 브랜드 상품만 반환된다.")
        @Test
        void returnsFilteredList_whenBrandIdIsProvided() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드"));
            productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, brand.getId()));
            productJpaRepository.save(new ProductModel("노브랜드상품", "브랜드 없음", 50000L, null));

            // act
            ParameterizedTypeReference<ApiResponse<List<ProductV1Dto.ProductSummaryResponse>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<ProductV1Dto.ProductSummaryResponse>>> response =
                testRestTemplate.exchange(ENDPOINT + "?brandId=" + brand.getId(), HttpMethod.GET, new HttpEntity<>(null), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data()).hasSize(1),
                () -> assertThat(response.getBody().data().get(0).name()).isEqualTo("에어맥스"),
                () -> assertThat(response.getBody().data().get(0).brandName()).isEqualTo("나이키")
            );
        }

        @DisplayName("sortBy=price_asc 이면, 가격 오름차순으로 반환된다.")
        @Test
        void returnsByPriceAsc_whenSortByIsPriceAsc() {
            // arrange
            productJpaRepository.save(new ProductModel("조던1", "나이키 농구화", 200000L, null));
            productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, null));

            // act
            ParameterizedTypeReference<ApiResponse<List<ProductV1Dto.ProductSummaryResponse>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<ProductV1Dto.ProductSummaryResponse>>> response =
                testRestTemplate.exchange(ENDPOINT + "?sortBy=price_asc", HttpMethod.GET, new HttpEntity<>(null), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().get(0).price()).isEqualTo(150000L)
            );
        }

        @DisplayName("sortBy=likes_desc 이면, 좋아요 내림차순으로 반환된다.")
        @Test
        void returnsByLikesDesc_whenSortByIsLikesDesc() {
            // arrange
            ProductModel popular = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, null));
            productJpaRepository.save(new ProductModel("조던1", "나이키 농구화", 200000L, null));
            // [fix] @Modifying 직접 호출은 트랜잭션 필요 → LikeFacade.addLike()로 대체
            likeFacade.addLike(1L, popular.getId());

            // act
            ParameterizedTypeReference<ApiResponse<List<ProductV1Dto.ProductSummaryResponse>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<ProductV1Dto.ProductSummaryResponse>>> response =
                testRestTemplate.exchange(ENDPOINT + "?sortBy=likes_desc", HttpMethod.GET, new HttpEntity<>(null), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().get(0).id()).isEqualTo(popular.getId())
            );
        }

        @DisplayName("잘못된 sortBy 값이면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenSortByIsInvalid() {
            // act
            ParameterizedTypeReference<ApiResponse<List<ProductV1Dto.ProductSummaryResponse>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<ProductV1Dto.ProductSummaryResponse>>> response =
                testRestTemplate.exchange(ENDPOINT + "?sortBy=invalid", HttpMethod.GET, new HttpEntity<>(null), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/products/{id}")
    @Nested
    class GetProduct {

        @DisplayName("존재하는 productId이면, 200 OK와 ProductResponse를 반환하며 description이 포함된다.")
        @Test
        void returnsProductResponse_whenProductExists() {
            // arrange
            ProductModel saved = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화 상세 설명", 150000L, null));

            // act
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response =
                testRestTemplate.exchange(ENDPOINT + "/" + saved.getId(), HttpMethod.GET, new HttpEntity<>(null), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().id()).isEqualTo(saved.getId()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("에어맥스"),
                () -> assertThat(response.getBody().data().description()).isEqualTo("나이키 운동화 상세 설명"),
                () -> assertThat(response.getBody().data().likeCount()).isEqualTo(0L)
            );
        }

        @DisplayName("존재하지 않는 productId이면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenProductNotExists() {
            // act
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response =
                testRestTemplate.exchange(ENDPOINT + "/999", HttpMethod.GET, new HttpEntity<>(null), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("숫자가 아닌 id이면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenIdIsNotNumber() {
            // act
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response =
                testRestTemplate.exchange(ENDPOINT + "/나나", HttpMethod.GET, new HttpEntity<>(null), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
