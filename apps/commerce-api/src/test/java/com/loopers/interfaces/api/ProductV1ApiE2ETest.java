package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.like.LikeV1Dto;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

  private final TestRestTemplate testRestTemplate;
  private final ProductJpaRepository productJpaRepository;
  private final DatabaseCleanUp databaseCleanUp;
  private final RedisCleanUp redisCleanUp;

  @Autowired
  public ProductV1ApiE2ETest(
      TestRestTemplate testRestTemplate,
      ProductJpaRepository productJpaRepository,
      DatabaseCleanUp databaseCleanUp,
      RedisCleanUp redisCleanUp) {
    this.testRestTemplate = testRestTemplate;
    this.productJpaRepository = productJpaRepository;
    this.databaseCleanUp = databaseCleanUp;
    this.redisCleanUp = redisCleanUp;
  }

  @AfterEach
  void tearDown() {
    databaseCleanUp.truncateAllTables();
    redisCleanUp.truncateAll();
  }

  private Long createProduct(String name, Long brandId, int likeCount) {
    ProductModel product =
        productJpaRepository.save(new ProductModel(name, "설명", 1000L, 10, brandId));
    for (int i = 0; i < likeCount; i++) {
      productJpaRepository.incrementLikeCount(product.getId());
    }
    return product.getId();
  }

  private ResponseEntity<ApiResponse<ProductV1Dto.ProductListResponse>> getProducts(String query) {
    return testRestTemplate.exchange(
        "/api/v1/products" + query,
        HttpMethod.GET,
        new HttpEntity<>(null),
        new ParameterizedTypeReference<>() {});
  }

  @DisplayName("GET /api/v1/products")
  @Nested
  class GetList {
    @DisplayName("brandId 필터를 주면, 해당 브랜드의 상품만 좋아요 순으로 반환한다.")
    @Test
    void returnsFilteredProducts_whenBrandIdIsProvided() {
      // arrange
      Long p1 = createProduct("상품1", 1L, 5);
      Long p2 = createProduct("상품2", 1L, 10);
      createProduct("상품3", 2L, 7);

      // act
      ResponseEntity<ApiResponse<ProductV1Dto.ProductListResponse>> response =
          getProducts("?brandId=1&sort=likes_desc");

      // assert
      List<ProductV1Dto.ProductResponse> items = response.getBody().data().items();
      assertAll(
          () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
          () -> assertThat(response.getBody().data().totalCount()).isEqualTo(2),
          () ->
              assertThat(items)
                  .extracting(ProductV1Dto.ProductResponse::id)
                  .containsExactly(p2, p1),
          () ->
              assertThat(items)
                  .extracting(ProductV1Dto.ProductResponse::likeCount)
                  .containsExactly(10L, 5L),
          () ->
              assertThat(items).extracting(ProductV1Dto.ProductResponse::brandId).containsOnly(1L));
    }

    @DisplayName("좋아요 순 정렬 시, like_count 내림차순으로 전체 상품을 반환한다.")
    @Test
    void returnsProductsSortedByLikeCount_whenSortIsLikesDesc() {
      // arrange
      Long p1 = createProduct("상품1", 1L, 5);
      Long p2 = createProduct("상품2", 1L, 10);
      Long p3 = createProduct("상품3", 2L, 7);

      // act
      ResponseEntity<ApiResponse<ProductV1Dto.ProductListResponse>> response =
          getProducts("?sort=likes_desc");

      // assert
      assertAll(
          () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
          () ->
              assertThat(response.getBody().data().items())
                  .extracting(ProductV1Dto.ProductResponse::id)
                  .containsExactly(p2, p3, p1));
    }

    @DisplayName("페이징 파라미터를 주면, 페이지 메타 정보와 함께 해당 페이지만 반환한다.")
    @Test
    void returnsPagedProducts_whenPagingIsProvided() {
      // arrange
      createProduct("상품1", 1L, 5);
      createProduct("상품2", 1L, 10);
      createProduct("상품3", 1L, 7);

      // act
      ResponseEntity<ApiResponse<ProductV1Dto.ProductListResponse>> response =
          getProducts("?sort=likes_desc&page=1&size=2");

      // assert
      ProductV1Dto.ProductListResponse data = response.getBody().data();
      assertAll(
          () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
          () -> assertThat(data.items()).hasSize(1),
          () -> assertThat(data.page()).isEqualTo(1),
          () -> assertThat(data.size()).isEqualTo(2),
          () -> assertThat(data.totalCount()).isEqualTo(3),
          () -> assertThat(data.totalPages()).isEqualTo(2));
    }

    @DisplayName("soft delete 된 상품은 목록에서 제외된다.")
    @Test
    void excludesSoftDeletedProducts() {
      // arrange
      Long alive = createProduct("상품1", 1L, 5);
      Long deletedId = createProduct("상품2", 1L, 10);
      ProductModel deleted = productJpaRepository.findById(deletedId).orElseThrow();
      deleted.delete();
      productJpaRepository.save(deleted);

      // act
      ResponseEntity<ApiResponse<ProductV1Dto.ProductListResponse>> response =
          getProducts("?sort=likes_desc");

      // assert
      assertAll(
          () -> assertThat(response.getBody().data().totalCount()).isEqualTo(1),
          () ->
              assertThat(response.getBody().data().items())
                  .extracting(ProductV1Dto.ProductResponse::id)
                  .containsExactly(alive));
    }

    @DisplayName("지원하지 않는 정렬 조건이면, 400 BAD_REQUEST 응답을 받는다.")
    @Test
    void throwsBadRequest_whenSortIsInvalid() {
      // act
      ResponseEntity<ApiResponse<ProductV1Dto.ProductListResponse>> response =
          getProducts("?sort=unknown");

      // assert
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  @DisplayName("좋아요 API 플로우")
  @Nested
  class LikeFlow {
    private ResponseEntity<ApiResponse<LikeV1Dto.LikeResponse>> exchangeLike(
        Long productId, HttpMethod method, String userId) {
      HttpHeaders headers = new HttpHeaders();
      if (userId != null) {
        headers.set("X-USER-ID", userId);
      }
      return testRestTemplate.exchange(
          "/api/v1/products/" + productId + "/likes",
          method,
          new HttpEntity<>(null, headers),
          new ParameterizedTypeReference<>() {});
    }

    private ResponseEntity<ApiResponse<ProductV1Dto.ProductDetailResponse>> getDetail(
        Long productId) {
      return testRestTemplate.exchange(
          "/api/v1/products/" + productId,
          HttpMethod.GET,
          new HttpEntity<>(null),
          new ParameterizedTypeReference<>() {});
    }

    @DisplayName("좋아요 등록/취소 후, 상세 조회의 likeCount 가 캐시 무효화를 거쳐 정확히 반영된다.")
    @Test
    void reflectsLikeCountInDetail_afterLikeAndUnlike() {
      // arrange
      Long productId = createProduct("상품1", 1L, 0);

      // act & assert — 상세 조회로 캐시 적재
      assertThat(getDetail(productId).getBody().data().likeCount()).isEqualTo(0L);

      // 좋아요 등록 → 캐시 무효화 → 상세에 반영
      ResponseEntity<ApiResponse<LikeV1Dto.LikeResponse>> likeResponse =
          exchangeLike(productId, HttpMethod.POST, "user-1");
      assertAll(
          () -> assertTrue(likeResponse.getStatusCode().is2xxSuccessful()),
          () -> assertThat(likeResponse.getBody().data().liked()).isTrue(),
          () -> assertThat(getDetail(productId).getBody().data().likeCount()).isEqualTo(1L));

      // 좋아요 취소 → 캐시 무효화 → 상세에 반영
      ResponseEntity<ApiResponse<LikeV1Dto.LikeResponse>> unlikeResponse =
          exchangeLike(productId, HttpMethod.DELETE, "user-1");
      assertAll(
          () -> assertTrue(unlikeResponse.getStatusCode().is2xxSuccessful()),
          () -> assertThat(unlikeResponse.getBody().data().liked()).isFalse(),
          () -> assertThat(getDetail(productId).getBody().data().likeCount()).isEqualTo(0L));
    }

    @DisplayName("X-USER-ID 헤더가 없으면, 400 BAD_REQUEST 응답을 받는다.")
    @Test
    void throwsBadRequest_whenUserIdHeaderIsMissing() {
      // arrange
      Long productId = createProduct("상품1", 1L, 0);

      // act
      ResponseEntity<ApiResponse<LikeV1Dto.LikeResponse>> response =
          exchangeLike(productId, HttpMethod.POST, null);

      // assert
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }
}
