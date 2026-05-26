package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.brand.BrandAdminV1Dto;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandAdminV1ApiE2ETest {

    private static final String ENDPOINT_REGISTER = "/api-admin/v1/brands";
    private static final String LDAP_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP = "loopers.admin";
    private static final ParameterizedTypeReference<ApiResponse<Map<String, Object>>> MAP_RESPONSE = new ParameterizedTypeReference<>() {};
    @Autowired
    private TestRestTemplate testRestTemplate;
    @Autowired
    private BrandJpaRepository brandJpaRepository;
    @Autowired
    private ProductJpaRepository productJpaRepository;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpEntity<Object> adminJsonRequest(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(LDAP_HEADER, ADMIN_LDAP);

        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Object> jsonRequestWithoutAdmin(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        return new HttpEntity<>(body, headers);
    }

    private BrandModel saveBrand(String name) {
        BrandModel brand = BrandModel.builder()
            .rawName(name)
            .rawDescription("감성을 담은 브랜드")
            .build();

        return brandJpaRepository.save(brand);
    }

    private HttpEntity<Void> adminGet() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(LDAP_HEADER, ADMIN_LDAP);

        return new HttpEntity<>(headers);
    }

    private HttpEntity<Void> guestGet() {
        return new HttpEntity<>(new HttpHeaders());
    }

    private ProductModel saveProduct(Long brandId, String name) {
        ProductModel product = ProductModel.builder()
            .brandId(brandId)
            .rawName(name)
            .rawDescription("포근한 감성 가디건")
            .rawPrice(39_000)
            .rawStock(50)
            .build();

        return productJpaRepository.save(product);
    }

    @DisplayName("브랜드 등록 - POST /api-admin/v1/brands")
    @Nested
    class CreateBrand {

        @DisplayName("정상 요청이면, 201 Created와 함께 brandId가 응답 본문에 담겨 반환된다.")
        @Test
        void returnsCreated_whenRequestIsValid() {
            // arrange
            BrandAdminV1Dto.CreateRequest requestBody = new BrandAdminV1Dto.CreateRequest("감성 브랜드", "감성을 담은 브랜드");

            // act
            ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                adminJsonRequest(requestBody),
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data()).containsOnlyKeys("brandId"),
                () -> assertThat(response.getBody().data().get("brandId")).isNotNull(),
                () -> assertThat(brandJpaRepository.findAll()).hasSize(1)
            );
        }

        @DisplayName("관리자 인증 헤더가 없으면, 403 Forbidden으로 거절되고 브랜드는 생성되지 않는다.")
        @Test
        void returnsForbidden_whenAdminHeaderIsMissing() {
            // arrange
            BrandAdminV1Dto.CreateRequest requestBody = new BrandAdminV1Dto.CreateRequest("감성 브랜드", "감성을 담은 브랜드");

            // act
            ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                jsonRequestWithoutAdmin(requestBody),
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.FORBIDDEN.getCode()),
                () -> assertThat(brandJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("브랜드 이름이 빈 값이면, 400 Bad Request로 거절되고 브랜드는 생성되지 않는다.")
        @Test
        void returnsBadRequest_whenNameIsBlank() {
            // arrange
            BrandAdminV1Dto.CreateRequest requestBody = new BrandAdminV1Dto.CreateRequest("   ", "감성을 담은 브랜드");

            // act
            ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                adminJsonRequest(requestBody),
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode()),
                () -> assertThat(brandJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("브랜드 이름이 50자를 초과하면, 400 Bad Request로 거절되고 브랜드는 생성되지 않는다.")
        @Test
        void returnsBadRequest_whenNameExceedsMaxLength() {
            // arrange
            BrandAdminV1Dto.CreateRequest requestBody = new BrandAdminV1Dto.CreateRequest("가".repeat(51), "감성을 담은 브랜드");

            // act
            ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                adminJsonRequest(requestBody),
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode()),
                () -> assertThat(brandJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("활성 이름이 중복되면, 409 Conflict로 거절되고 추가 생성되지 않는다.")
        @Test
        void returnsConflict_whenActiveNameIsDuplicated() {
            // arrange
            saveBrand("감성 브랜드");
            BrandAdminV1Dto.CreateRequest requestBody = new BrandAdminV1Dto.CreateRequest("감성 브랜드", "다른 설명");

            // act
            ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                adminJsonRequest(requestBody),
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.CONFLICT.getCode()),
                () -> assertThat(brandJpaRepository.findAll()).hasSize(1)
            );
        }

        @DisplayName("같은 이름의 브랜드가 삭제된 상태로만 존재하면, 201 Created로 재등록된다.")
        @Test
        void returnsCreated_whenSameNameBrandIsDeleted() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            brand.delete();
            brandJpaRepository.saveAndFlush(brand);
            BrandAdminV1Dto.CreateRequest requestBody = new BrandAdminV1Dto.CreateRequest("감성 브랜드", "감성을 담은 브랜드");

            // act
            ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                adminJsonRequest(requestBody),
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data()).containsOnlyKeys("brandId"),
                () -> assertThat(response.getBody().data().get("brandId")).isNotNull(),
                () -> assertThat(brandJpaRepository.findAll()).hasSize(2)
            );
        }
    }

    @DisplayName("브랜드 수정 - PUT /api-admin/v1/brands/{brandId}")
    @Nested
    class UpdateBrand {

        @DisplayName("정상 요청이면, 200 OK와 함께 brandId가 반환되고 이름이 갱신된다.")
        @Test
        void returnsOk_andUpdatesName_whenRequestIsValid() {
            // arrange
            BrandModel savedBrand = saveBrand("기존 브랜드");
            BrandAdminV1Dto.UpdateRequest requestBody = new BrandAdminV1Dto.UpdateRequest("새 브랜드", "새 설명");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/" + savedBrand.getId(),
                HttpMethod.PUT,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            BrandModel reloadedBrand = brandJpaRepository.findById(savedBrand.getId()).orElseThrow();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data().get("brandId")).isNotNull(),
                () -> assertThat(reloadedBrand.getName().value()).isEqualTo("새 브랜드")
            );
        }

        @DisplayName("관리자 인증 헤더가 없으면, 403 Forbidden으로 거절된다.")
        @Test
        void returnsForbidden_whenAdminHeaderIsMissing() {
            // arrange
            BrandModel savedBrand = saveBrand("기존 브랜드");
            BrandAdminV1Dto.UpdateRequest requestBody = new BrandAdminV1Dto.UpdateRequest("새 브랜드", "새 설명");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/" + savedBrand.getId(),
                HttpMethod.PUT,
                jsonRequestWithoutAdmin(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.FORBIDDEN.getCode())
            );
        }

        @DisplayName("대상 브랜드가 존재하지 않으면, 404 Not Found로 거절된다.")
        @Test
        void returnsNotFound_whenTargetIsAbsent() {
            // arrange
            BrandAdminV1Dto.UpdateRequest requestBody = new BrandAdminV1Dto.UpdateRequest("새 브랜드", "새 설명");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/99999",
                HttpMethod.PUT,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.NOT_FOUND.getCode())
            );
        }

        @DisplayName("이름이 50자를 초과하면, 400 Bad Request로 거절된다.")
        @Test
        void returnsBadRequest_whenNameExceedsMaxLength() {
            // arrange
            BrandModel savedBrand = saveBrand("기존 브랜드");
            BrandAdminV1Dto.UpdateRequest requestBody = new BrandAdminV1Dto.UpdateRequest("가".repeat(51), "새 설명");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/" + savedBrand.getId(),
                HttpMethod.PUT,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode())
            );
        }

        @DisplayName("새 이름이 다른 활성 브랜드와 중복되면, 409 Conflict로 거절된다.")
        @Test
        void returnsConflict_whenNewNameDuplicatesOtherActive() {
            // arrange
            saveBrand("이미 있는 브랜드");
            BrandModel target = saveBrand("수정 대상 브랜드");
            BrandAdminV1Dto.UpdateRequest requestBody = new BrandAdminV1Dto.UpdateRequest("이미 있는 브랜드", "새 설명");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/" + target.getId(),
                HttpMethod.PUT,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.CONFLICT.getCode())
            );
        }

        @DisplayName("기존과 동일한 이름으로 수정해도, 200 OK로 정상 처리된다.")
        @Test
        void returnsOk_whenUpdatedWithSameName() {
            // arrange
            BrandModel savedBrand = saveBrand("감성 브랜드");
            BrandAdminV1Dto.UpdateRequest requestBody = new BrandAdminV1Dto.UpdateRequest("감성 브랜드", "새 설명");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/" + savedBrand.getId(),
                HttpMethod.PUT,
                adminJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @DisplayName("브랜드 목록 - GET /api-admin/v1/brands")
    @Nested
    class ReadBrands {

        @DisplayName("정상 요청이면, 200 OK와 함께 삭제되지 않은 브랜드 목록과 페이지 메타가 반환된다.")
        @Test
        void returnsOk_withActiveBrandsAndMeta() {
            // arrange
            saveBrand("브랜드1");
            saveBrand("브랜드2");
            BrandModel deletedBrand = saveBrand("브랜드3");
            deletedBrand.delete();
            brandJpaRepository.saveAndFlush(deletedBrand);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "?page=0&size=20",
                HttpMethod.GET,
                adminGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data())
                    .containsKeys("content", "page", "size", "totalElements", "totalPages"),
                () -> assertThat((java.util.List<?>)response.getBody().data().get("content")).hasSize(2),
                () -> assertThat(((Number)response.getBody().data().get("totalElements")).longValue()).isEqualTo(2L)
            );
        }

        @DisplayName("활성 브랜드가 없으면, 200 OK와 함께 빈 목록이 반환된다.")
        @Test
        void returnsOk_withEmptyContent_whenNoBrands() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "?page=0&size=20",
                HttpMethod.GET,
                adminGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat((java.util.List<?>)response.getBody().data().get("content")).isEmpty(),
                () -> assertThat(((Number)response.getBody().data().get("totalElements")).longValue()).isEqualTo(0L)
            );
        }

        @DisplayName("관리자 인증 헤더가 없으면, 403 Forbidden으로 거절된다.")
        @Test
        void returnsForbidden_whenAdminHeaderIsMissing() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "?page=0&size=20",
                HttpMethod.GET,
                guestGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.FORBIDDEN.getCode())
            );
        }

        @DisplayName("size가 허용 범위를 벗어나면, 400 Bad Request로 거절된다.")
        @Test
        void returnsBadRequest_whenSizeOutOfRange() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "?page=0&size=101",
                HttpMethod.GET,
                adminGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode())
            );
        }

        @DisplayName("page가 음수면, 400 Bad Request로 거절된다.")
        @Test
        void returnsBadRequest_whenPageIsNegative() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "?page=-1&size=20",
                HttpMethod.GET,
                adminGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode())
            );
        }
    }

    @DisplayName("브랜드 상세 - GET /api-admin/v1/brands/{brandId}")
    @Nested
    class ReadBrandDetail {

        @DisplayName("정상 요청이면, 200 OK와 함께 등록·갱신 시각을 포함한 상세가 반환된다.")
        @Test
        void returnsOk_withDetailFields() {
            // arrange
            BrandModel savedBrand = saveBrand("감성 브랜드");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/" + savedBrand.getId(),
                HttpMethod.GET,
                adminGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data())
                    .containsOnlyKeys("brandId", "name", "description", "createdAt", "updatedAt"),
                () -> assertThat(response.getBody().data().get("name")).isEqualTo("감성 브랜드")
            );
        }

        @DisplayName("관리자 인증 헤더가 없으면, 403 Forbidden으로 거절된다.")
        @Test
        void returnsForbidden_whenAdminHeaderIsMissing() {
            // arrange
            BrandModel savedBrand = saveBrand("감성 브랜드");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/" + savedBrand.getId(),
                HttpMethod.GET,
                guestGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.FORBIDDEN.getCode())
            );
        }

        @DisplayName("대상 브랜드가 존재하지 않으면, 404 Not Found로 거절된다.")
        @Test
        void returnsNotFound_whenTargetIsAbsent() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/99999",
                HttpMethod.GET,
                adminGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.NOT_FOUND.getCode())
            );
        }
    }

    @DisplayName("브랜드 삭제 - DELETE /api-admin/v1/brands/{brandId}")
    @Nested
    class DeleteBrand {

        @DisplayName("정상 요청이면, 200 OK로 처리되고 브랜드와 소속 활성 상품이 함께 삭제되며 다른 브랜드 상품은 유지된다.")
        @Test
        void returnsOk_andCascadesProducts_whenRequestIsValid() {
            // arrange
            BrandModel targetBrand = saveBrand("삭제 대상 브랜드");
            BrandModel otherBrand = saveBrand("다른 브랜드");
            ProductModel product1 = saveProduct(targetBrand.getId(), "대상 상품1");
            ProductModel product2 = saveProduct(targetBrand.getId(), "대상 상품2");
            ProductModel otherBrandProduct = saveProduct(otherBrand.getId(), "다른 브랜드 상품");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/" + targetBrand.getId(),
                HttpMethod.DELETE,
                adminGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(brandJpaRepository.findById(targetBrand.getId()).orElseThrow().getDeletedAt()).isNotNull(),
                () -> assertThat(productJpaRepository.findById(product1.getId()).orElseThrow().getDeletedAt()).isNotNull(),
                () -> assertThat(productJpaRepository.findById(product2.getId()).orElseThrow().getDeletedAt()).isNotNull(),
                () -> assertThat(productJpaRepository.findById(otherBrandProduct.getId()).orElseThrow().getDeletedAt()).isNull()
            );
        }

        @DisplayName("동일한 브랜드에 삭제를 두 번 요청해도, 두 응답 모두 200 OK로 마무리된다(멱등).")
        @Test
        void returnsOk_whenDeletedTwice() {
            // arrange
            BrandModel targetBrand = saveBrand("삭제 대상 브랜드");
            String endpoint = ENDPOINT_REGISTER + "/" + targetBrand.getId();

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> firstResponse = testRestTemplate.exchange(
                endpoint, HttpMethod.DELETE, adminGet(), MAP_RESPONSE);
            ResponseEntity<ApiResponse<Map<String, Object>>> secondResponse = testRestTemplate.exchange(
                endpoint, HttpMethod.DELETE, adminGet(), MAP_RESPONSE);

            // assert
            assertAll(
                () -> assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(secondResponse.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)
            );
        }

        @DisplayName("대상 브랜드가 존재하지 않아도, 200 OK로 마무리된다(멱등).")
        @Test
        void returnsOk_whenTargetIsAbsent() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/99999",
                HttpMethod.DELETE,
                adminGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)
            );
        }

        @DisplayName("관리자 인증 헤더가 없으면, 403 Forbidden으로 거절된다.")
        @Test
        void returnsForbidden_whenAdminHeaderIsMissing() {
            // arrange
            BrandModel targetBrand = saveBrand("삭제 대상 브랜드");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER + "/" + targetBrand.getId(),
                HttpMethod.DELETE,
                guestGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.FORBIDDEN.getCode())
            );
        }
    }
}
