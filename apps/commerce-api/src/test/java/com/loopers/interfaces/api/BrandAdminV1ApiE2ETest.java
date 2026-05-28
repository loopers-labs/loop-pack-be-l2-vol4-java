package com.loopers.interfaces.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.loopers.application.brand.BrandAdminInfo;
import com.loopers.application.brand.BrandFacade;
import com.loopers.domain.brand.BrandModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.interfaces.api.brand.BrandAdminV1Dto;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandAdminV1ApiE2ETest {

    private static final String COLLECTION = "/api/v1/admin/brands";
    private static final String ITEM = "/api/v1/admin/brands/{brandId}";

    private final TestRestTemplate testRestTemplate;
    private final BrandFacade brandFacade;
    private final BrandJpaRepository brandJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public BrandAdminV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandFacade brandFacade,
        BrandJpaRepository brandJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandFacade = brandFacade;
        this.brandJpaRepository = brandJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/admin/brands")
    @Nested
    class Create {

        @DisplayName("유효한 요청이면, 200 과 신규 브랜드 정보(deletedAt=null)를 반환한다.")
        @Test
        void returnsCreatedBrand_whenRequestIsValid() {
            // given
            BrandAdminV1Dto.CreateRequest request = new BrandAdminV1Dto.CreateRequest("나이키", "Just Do It");

            // when
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.Response>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandAdminV1Dto.Response>> response = testRestTemplate.exchange(
                COLLECTION, HttpMethod.POST, new HttpEntity<>(request), responseType
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data().id()).isNotNull(),
                () -> assertThat(response.getBody().data().name()).isEqualTo("나이키"),
                () -> assertThat(response.getBody().data().description()).isEqualTo("Just Do It"),
                () -> assertThat(response.getBody().data().deletedAt()).isNull()
            );
        }

        @DisplayName("이름이 이미 존재하면, 409 와 DUPLICATE_BRAND_NAME 코드를 반환한다.")
        @Test
        void returnsConflict_whenNameAlreadyExists() {
            // given
            brandFacade.create("나이키", "Just Do It");
            BrandAdminV1Dto.CreateRequest duplicate = new BrandAdminV1Dto.CreateRequest("나이키", "다른 설명");

            // when
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.Response>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandAdminV1Dto.Response>> response = testRestTemplate.exchange(
                COLLECTION, HttpMethod.POST, new HttpEntity<>(duplicate), responseType
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("DUPLICATE_BRAND_NAME")
            );
        }
    }

    @DisplayName("GET /api/v1/admin/brands")
    @Nested
    class ListBrands {

        @DisplayName("등록된 모든 브랜드를 페이지 메타와 함께 반환한다.")
        @Test
        void returnsPagedBrands_withMeta() {
            // given
            brandFacade.create("나이키", "Just Do It");
            brandFacade.create("아디다스", "Impossible is Nothing");

            // when
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.PageResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandAdminV1Dto.PageResponse>> response = testRestTemplate.exchange(
                COLLECTION, HttpMethod.GET, HttpEntity.EMPTY, responseType
            );

            // then
            BrandAdminV1Dto.PageResponse body = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(body.content()).hasSize(2),
                () -> assertThat(body.page()).isEqualTo(0),
                () -> assertThat(body.size()).isEqualTo(20),
                () -> assertThat(body.totalElements()).isEqualTo(2),
                () -> assertThat(body.totalPages()).isEqualTo(1)
            );
        }

        @DisplayName("soft-delete 된 브랜드도 목록에 포함되고, deletedAt 이 노출된다.")
        @Test
        void includesSoftDeletedBrand_withDeletedAt() {
            // given
            BrandAdminInfo deleted = brandFacade.create("폐기", "deprecated");
            brandFacade.create("나이키", "Just Do It");
            BrandModel toDelete = brandJpaRepository.findById(deleted.id()).orElseThrow();
            toDelete.delete();
            brandJpaRepository.save(toDelete);

            // when
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.PageResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandAdminV1Dto.PageResponse>> response = testRestTemplate.exchange(
                COLLECTION, HttpMethod.GET, HttpEntity.EMPTY, responseType
            );

            // then
            BrandAdminV1Dto.Response deletedItem = response.getBody().data().content().stream()
                .filter(item -> item.id().equals(deleted.id()))
                .findFirst()
                .orElseThrow();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2),
                () -> assertThat(deletedItem.deletedAt()).isNotNull()
            );
        }

        @DisplayName("size=2 로 조회하면, 최대 2건만 반환되고 totalPages 가 계산된다.")
        @Test
        void respectsSizeQueryParameter() {
            // given
            for (int i = 1; i <= 5; i++) {
                brandFacade.create("브랜드" + i, "설명" + i);
            }

            // when
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.PageResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandAdminV1Dto.PageResponse>> response = testRestTemplate.exchange(
                COLLECTION + "?page=0&size=2", HttpMethod.GET, HttpEntity.EMPTY, responseType
            );

            // then
            BrandAdminV1Dto.PageResponse body = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(body.content()).hasSize(2),
                () -> assertThat(body.totalElements()).isEqualTo(5),
                () -> assertThat(body.totalPages()).isEqualTo(3)
            );
        }
    }

    @DisplayName("GET /api/v1/admin/brands/{brandId}")
    @Nested
    class GetBrand {

        @DisplayName("활성 브랜드 id 로 요청하면, 200 과 브랜드 정보(deletedAt=null)를 반환한다.")
        @Test
        void returnsBrand_whenBrandIsActive() {
            // given
            BrandAdminInfo created = brandFacade.create("나이키", "Just Do It");

            // when
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.Response>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandAdminV1Dto.Response>> response = testRestTemplate.exchange(
                ITEM, HttpMethod.GET, HttpEntity.EMPTY, responseType, created.id()
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isEqualTo(created.id()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("나이키"),
                () -> assertThat(response.getBody().data().description()).isEqualTo("Just Do It"),
                () -> assertThat(response.getBody().data().deletedAt()).isNull()
            );
        }

        @DisplayName("soft-delete 된 브랜드 id 로 요청해도, 200 과 deletedAt 이 노출된 정보를 반환한다.")
        @Test
        void returnsBrand_whenBrandIsSoftDeleted() {
            // given
            BrandAdminInfo created = brandFacade.create("나이키", "Just Do It");
            BrandModel brand = brandJpaRepository.findById(created.id()).orElseThrow();
            brand.delete();
            brandJpaRepository.save(brand);

            // when
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.Response>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandAdminV1Dto.Response>> response = testRestTemplate.exchange(
                ITEM, HttpMethod.GET, HttpEntity.EMPTY, responseType, created.id()
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isEqualTo(created.id()),
                () -> assertThat(response.getBody().data().deletedAt()).isNotNull()
            );
        }

        @DisplayName("응답 JSON 의 data 객체에는 createdAt/updatedAt 이 포함되지 않는다.")
        @Test
        void doesNotExposeCreatedAtOrUpdatedAt_inResponseJson() {
            // given
            BrandAdminInfo created = brandFacade.create("나이키", "Just Do It");
            BrandModel brand = brandJpaRepository.findById(created.id()).orElseThrow();
            brand.delete();
            brandJpaRepository.save(brand);

            // when
            ResponseEntity<JsonNode> response = testRestTemplate.exchange(
                ITEM, HttpMethod.GET, HttpEntity.EMPTY, JsonNode.class, created.id()
            );

            // then
            JsonNode data = response.getBody().get("data");
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.has("id")).isTrue(),
                () -> assertThat(data.has("deletedAt")).isTrue(),
                () -> assertThat(data.has("createdAt")).isFalse(),
                () -> assertThat(data.has("updatedAt")).isFalse()
            );
        }

        @DisplayName("존재하지 않는 id 로 요청하면, 404 와 BRAND_NOT_FOUND 코드를 반환한다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
            // given
            Long missingId = 9_999L;

            // when
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.Response>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandAdminV1Dto.Response>> response = testRestTemplate.exchange(
                ITEM, HttpMethod.GET, HttpEntity.EMPTY, responseType, missingId
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("BRAND_NOT_FOUND")
            );
        }
    }

    @DisplayName("PATCH /api/v1/admin/brands/{brandId}")
    @Nested
    class UpdateBrand {

        @DisplayName("이름과 설명을 모두 전달하면, 200 과 변경된 정보를 반환한다.")
        @Test
        void returnsUpdatedBrand_whenBothFieldsAreGiven() {
            // given
            BrandAdminInfo created = brandFacade.create("나이키", "Just Do It");
            BrandAdminV1Dto.UpdateRequest request = new BrandAdminV1Dto.UpdateRequest("아디다스", "Impossible is Nothing");

            // when
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.Response>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandAdminV1Dto.Response>> response = testRestTemplate.exchange(
                ITEM, HttpMethod.PATCH, new HttpEntity<>(request), responseType, created.id()
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().name()).isEqualTo("아디다스"),
                () -> assertThat(response.getBody().data().description()).isEqualTo("Impossible is Nothing")
            );
        }

        @DisplayName("설명만 전달하면, 설명만 변경되고 이름은 유지된다.")
        @Test
        void returnsUpdatedBrand_whenOnlyDescriptionIsGiven() {
            // given
            BrandAdminInfo created = brandFacade.create("나이키", "Just Do It");
            BrandAdminV1Dto.UpdateRequest request = new BrandAdminV1Dto.UpdateRequest(null, "새 슬로건");

            // when
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.Response>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandAdminV1Dto.Response>> response = testRestTemplate.exchange(
                ITEM, HttpMethod.PATCH, new HttpEntity<>(request), responseType, created.id()
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().name()).isEqualTo("나이키"),
                () -> assertThat(response.getBody().data().description()).isEqualTo("새 슬로건")
            );
        }

        @DisplayName("새 이름이 다른 브랜드와 중복되면, 409 와 DUPLICATE_BRAND_NAME 코드를 반환한다.")
        @Test
        void returnsConflict_whenNewNameClashesWithAnotherBrand() {
            // given
            brandFacade.create("나이키", "Just Do It");
            BrandAdminInfo target = brandFacade.create("아디다스", "Impossible is Nothing");
            BrandAdminV1Dto.UpdateRequest request = new BrandAdminV1Dto.UpdateRequest("나이키", null);

            // when
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.Response>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandAdminV1Dto.Response>> response = testRestTemplate.exchange(
                ITEM, HttpMethod.PATCH, new HttpEntity<>(request), responseType, target.id()
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("DUPLICATE_BRAND_NAME")
            );
        }

        @DisplayName("soft-delete 된 브랜드를 수정하려고 하면, 404 와 BRAND_NOT_FOUND 코드를 반환한다.")
        @Test
        void returnsNotFound_whenBrandIsSoftDeleted() {
            // given
            BrandAdminInfo created = brandFacade.create("나이키", "Just Do It");
            BrandModel brand = brandJpaRepository.findById(created.id()).orElseThrow();
            brand.delete();
            brandJpaRepository.save(brand);
            BrandAdminV1Dto.UpdateRequest request = new BrandAdminV1Dto.UpdateRequest("아디다스", null);

            // when
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.Response>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandAdminV1Dto.Response>> response = testRestTemplate.exchange(
                ITEM, HttpMethod.PATCH, new HttpEntity<>(request), responseType, created.id()
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("BRAND_NOT_FOUND")
            );
        }

        @DisplayName("존재하지 않는 id 로 수정하면, 404 와 BRAND_NOT_FOUND 코드를 반환한다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
            // given
            Long missingId = 9_999L;
            BrandAdminV1Dto.UpdateRequest request = new BrandAdminV1Dto.UpdateRequest("아디다스", null);

            // when
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.Response>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandAdminV1Dto.Response>> response = testRestTemplate.exchange(
                ITEM, HttpMethod.PATCH, new HttpEntity<>(request), responseType, missingId
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("BRAND_NOT_FOUND")
            );
        }
    }
}
