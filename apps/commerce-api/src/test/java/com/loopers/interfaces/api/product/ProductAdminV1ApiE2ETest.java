package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductService;
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
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.apiadmin.product.ProductAdminV1Dto;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductAdminV1ApiE2ETest {

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductService productService;
    @Autowired private ProductFacade productFacade;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final String ADMIN_ID = "admin1";
    private static final String ADMIN_PW = "Admin1234!";

    private BrandModel brand;

    @BeforeEach
    void setUp() {
        userRepository.save(new UserModel(
                new UserId(ADMIN_ID),
                new Password(passwordEncoder.encode(ADMIN_PW)),
                new Name("관리자"),
                new BirthDay("1990-01-01"),
                new Email("admin@test.com"),
                UserRole.ADMIN
        ));
        brand = brandRepository.save(new BrandModel("테스트브랜드"));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Loopers-LoginId", ADMIN_ID);
        headers.set("X-Loopers-LoginPw", ADMIN_PW);
        return headers;
    }

    private ProductModel saveDefaultProduct(String name) {
        return productRepository.save(new ProductModel(brand.getId(), new ProductName(name)));
    }

    private ProductInfo registerDefaultProductWithStock(String name) {
        return productFacade.registerProduct(brand.getId(), name, 10000L, 5);
    }

    @DisplayName("POST /api-admin/v1/products")
    @Nested
    class Register {

        @DisplayName("유효한 입력이면, 201 CREATED 와 상품 정보를 반환한다.")
        @Test
        void returnsProduct_whenInputsAreValid() {
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductResponse>> type = new ParameterizedTypeReference<>() {};
            HttpEntity<ProductAdminV1Dto.RegisterRequest> request = new HttpEntity<>(
                    new ProductAdminV1Dto.RegisterRequest(brand.getId(), "테스트상품", 10000L, 5), adminHeaders());

            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange("/api-admin/v1/products", HttpMethod.POST, request, type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().data().name()).isEqualTo("테스트상품");
            assertThat(response.getBody().data().brandId()).isEqualTo(brand.getId());
            assertThat(response.getBody().data().likeCount()).isEqualTo(0L);
            assertThat(response.getBody().data().stocks()).hasSize(1);
            assertThat(response.getBody().data().stocks().get(0).price()).isEqualTo(10000L);
            assertThat(response.getBody().data().stocks().get(0).quantity()).isEqualTo(5);
        }
    }

    @DisplayName("GET /api-admin/v1/products/{productId}")
    @Nested
    class GetProduct {

        @DisplayName("상품이 존재하면, 200 OK 와 재고 정보를 포함한 상품 정보를 반환한다.")
        @Test
        void returnsProductWithStockInfo_whenProductExists() {
            ProductInfo registered = registerDefaultProductWithStock("테스트상품");
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductResponse>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange("/api-admin/v1/products/" + registered.id(), HttpMethod.GET,
                            new HttpEntity<>(adminHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().id()).isEqualTo(registered.id());
            assertThat(response.getBody().data().name()).isEqualTo("테스트상품");
            assertThat(response.getBody().data().likeCount()).isEqualTo(0L);
            assertThat(response.getBody().data().stocks()).hasSize(1);
            assertThat(response.getBody().data().stocks().get(0).id()).isEqualTo(registered.stocks().get(0).id());
            assertThat(response.getBody().data().stocks().get(0).price()).isEqualTo(10000L);
            assertThat(response.getBody().data().stocks().get(0).quantity()).isEqualTo(5);
        }
    }

    @DisplayName("GET /api-admin/v1/products")
    @Nested
    class GetList {

        @DisplayName("삭제되지 않은 상품만 페이징되어 반환된다.")
        @Test
        void returnsOnlyActiveProductsWithPaging() {
            saveDefaultProduct("상품A");
            saveDefaultProduct("상품B");
            ProductModel deleted = saveDefaultProduct("상품C");
            productService.delete(deleted.getId());

            ParameterizedTypeReference<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>> response =
                    testRestTemplate.exchange("/api-admin/v1/products?page=0&size=10", HttpMethod.GET,
                            new HttpEntity<>(adminHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().totalElements()).isEqualTo(2);
            assertThat(response.getBody().data().content()).hasSize(2);
        }
    }

    @DisplayName("PUT /api-admin/v1/products/{productId}")
    @Nested
    class Update {

        @DisplayName("상품명만 입력하면, 200 OK 와 변경된 상품명을 반환한다.")
        @Test
        void updatesName_whenOnlyNameIsProvided() {
            ProductInfo registered = registerDefaultProductWithStock("테스트상품");
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductResponse>> type = new ParameterizedTypeReference<>() {};
            HttpEntity<ProductAdminV1Dto.UpdateRequest> request = new HttpEntity<>(
                    new ProductAdminV1Dto.UpdateRequest("수정상품", null, null, null), adminHeaders());

            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange("/api-admin/v1/products/" + registered.id(), HttpMethod.PUT, request, type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().name()).isEqualTo("수정상품");
            assertThat(response.getBody().data().likeCount()).isEqualTo(0L);
        }

        @DisplayName("재고 가격만 입력하면, 200 OK 를 반환한다.")
        @Test
        void updatesStockPrice_whenOnlyStockPriceIsProvided() {
            ProductInfo registered = registerDefaultProductWithStock("테스트상품");
            Long stockId = registered.stocks().get(0).id();
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductResponse>> type = new ParameterizedTypeReference<>() {};
            HttpEntity<ProductAdminV1Dto.UpdateRequest> request = new HttpEntity<>(
                    new ProductAdminV1Dto.UpdateRequest(null, stockId, 20000L, null), adminHeaders());

            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange("/api-admin/v1/products/" + registered.id(), HttpMethod.PUT, request, type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("양수 재고 증감량 입력하면, 200 OK 를 반환한다.")
        @Test
        void increasesStock_whenStockQuantityIsPositive() {
            ProductInfo registered = registerDefaultProductWithStock("테스트상품");
            Long stockId = registered.stocks().get(0).id();
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductResponse>> type = new ParameterizedTypeReference<>() {};
            HttpEntity<ProductAdminV1Dto.UpdateRequest> request = new HttpEntity<>(
                    new ProductAdminV1Dto.UpdateRequest(null, stockId, null, 5), adminHeaders());

            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange("/api-admin/v1/products/" + registered.id(), HttpMethod.PUT, request, type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("음수 재고 증감량 입력하면, 200 OK 를 반환한다.")
        @Test
        void decreasesStock_whenStockQuantityIsNegative() {
            ProductInfo registered = registerDefaultProductWithStock("테스트상품");
            Long stockId = registered.stocks().get(0).id();
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductResponse>> type = new ParameterizedTypeReference<>() {};
            HttpEntity<ProductAdminV1Dto.UpdateRequest> request = new HttpEntity<>(
                    new ProductAdminV1Dto.UpdateRequest(null, stockId, null, -3), adminHeaders());

            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange("/api-admin/v1/products/" + registered.id(), HttpMethod.PUT, request, type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("수정 항목을 하나도 입력하지 않으면, 400 BAD_REQUEST 를 반환한다.")
        @Test
        void returnsBadRequest_whenNoFieldIsProvided() {
            ProductInfo registered = registerDefaultProductWithStock("테스트상품");
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            HttpEntity<ProductAdminV1Dto.UpdateRequest> request = new HttpEntity<>(
                    new ProductAdminV1Dto.UpdateRequest(null, null, null, null), adminHeaders());

            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange("/api-admin/v1/products/" + registered.id(), HttpMethod.PUT, request, type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("DELETE /api-admin/v1/products/{productId}")
    @Nested
    class Delete {

        @DisplayName("상품이 존재하면, 200 OK 를 반환한다.")
        @Test
        void returnsOk_whenProductExists() {
            ProductModel product = saveDefaultProduct("테스트상품");
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange("/api-admin/v1/products/" + product.getId(), HttpMethod.DELETE,
                            new HttpEntity<>(adminHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
