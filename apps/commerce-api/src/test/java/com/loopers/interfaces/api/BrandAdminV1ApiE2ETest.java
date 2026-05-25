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
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.interfaces.api.brand.BrandAdminV1Dto;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandAdminV1ApiE2ETest {

    private static final String ENDPOINT_REGISTER = "/api-admin/v1/brands";
    private static final String LDAP_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP = "loopers.admin";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

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
}
