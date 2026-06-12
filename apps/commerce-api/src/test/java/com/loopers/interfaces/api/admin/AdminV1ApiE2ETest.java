package com.loopers.interfaces.api.admin;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.product.Product;
import com.loopers.infrastructure.brand.BrandJpaEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.order.OrderJpaEntity;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.product.ProductJpaEntity;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.brand.BrandDto;
import com.loopers.interfaces.api.coupon.CouponDto;
import com.loopers.interfaces.api.order.OrderDto;
import com.loopers.interfaces.api.product.ProductDto;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminV1ApiE2ETest {

    private static final String ENDPOINT_ADMIN_BRANDS = "/api-admin/v1/brands";
    private static final String ENDPOINT_ADMIN_PRODUCTS = "/api-admin/v1/products";
    private static final String ENDPOINT_ADMIN_ORDERS = "/api-admin/v1/orders";
    private static final String ENDPOINT_ADMIN_COUPONS = "/api-admin/v1/coupons";

    private final TestRestTemplate testRestTemplate;
    private final CouponFacade couponFacade;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final OrderJpaRepository orderJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    AdminV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        CouponFacade couponFacade,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        OrderJpaRepository orderJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.couponFacade = couponFacade;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.orderJpaRepository = orderJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @DisplayName("Coupon Admin API")
    @Nested
    class CouponAdmin {

        @DisplayName("쿠폰 템플릿을 생성, 조회, 수정, 삭제할 수 있다.")
        @Test
        void managesCouponTemplateCrud_whenAdminAuthenticated() {
            // arrange
            ZonedDateTime expiredAt = ZonedDateTime.now().plusDays(7);
            CouponDto.Create.V1.Request createRequest = new CouponDto.Create.V1.Request(
                "신규가입 10% 할인",
                CouponType.RATE,
                10L,
                10_000L,
                expiredAt
            );

            // act
            ResponseEntity<ApiResponse<CouponDto.Create.V1.Response>> createResponse =
                testRestTemplate.exchange(
                    ENDPOINT_ADMIN_COUPONS,
                    HttpMethod.POST,
                    new HttpEntity<>(createRequest, adminHeaders()),
                    couponCreateResponseType()
                );
            Long couponId = createResponse.getBody().data().id();

            ResponseEntity<ApiResponse<CouponDto.Template.Response>> getResponse =
                testRestTemplate.exchange(
                    ENDPOINT_ADMIN_COUPONS + "/" + couponId,
                    HttpMethod.GET,
                    new HttpEntity<>(adminHeaders()),
                    couponGetResponseType()
                );

            CouponDto.Update.V1.Request updateRequest = new CouponDto.Update.V1.Request(
                "신규가입 1000원 할인",
                CouponType.FIXED,
                1_000L,
                5_000L,
                expiredAt.plusDays(1)
            );
            ResponseEntity<ApiResponse<CouponDto.Update.V1.Response>> updateResponse =
                testRestTemplate.exchange(
                    ENDPOINT_ADMIN_COUPONS + "/" + couponId,
                    HttpMethod.PUT,
                    new HttpEntity<>(updateRequest, adminHeaders()),
                    couponUpdateResponseType()
                );

            ResponseEntity<ApiResponse<List<CouponDto.Template.Response>>> listResponse =
                testRestTemplate.exchange(
                    ENDPOINT_ADMIN_COUPONS,
                    HttpMethod.GET,
                    new HttpEntity<>(adminHeaders()),
                    couponListResponseType()
                );

            ResponseEntity<ApiResponse<Void>> deleteResponse =
                testRestTemplate.exchange(
                    ENDPOINT_ADMIN_COUPONS + "/" + couponId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(adminHeaders()),
                    voidResponseType()
                );

            ResponseEntity<ApiResponse<CouponDto.Template.Response>> getDeletedResponse =
                testRestTemplate.exchange(
                    ENDPOINT_ADMIN_COUPONS + "/" + couponId,
                    HttpMethod.GET,
                    new HttpEntity<>(adminHeaders()),
                    couponGetResponseType()
                );

            // assert
            assertAll(
                () -> assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(createResponse.getBody().data().type()).isEqualTo(CouponType.RATE),
                () -> assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(getResponse.getBody().data().name()).isEqualTo("신규가입 10% 할인"),
                () -> assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(updateResponse.getBody().data().type()).isEqualTo(CouponType.FIXED),
                () -> assertThat(updateResponse.getBody().data().value()).isEqualTo(1_000L),
                () -> assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(listResponse.getBody().data()).extracting(CouponDto.Template.Response::id)
                    .contains(couponId),
                () -> assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(getDeletedResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        }

        @DisplayName("특정 쿠폰의 발급 내역을 조회할 수 있다.")
        @Test
        void returnsIssuedCoupons_whenAdminAuthenticated() {
            // arrange
            CouponInfo.Template coupon = couponFacade.createCoupon(
                "신규가입 10% 할인",
                CouponType.RATE,
                10L,
                10_000L,
                ZonedDateTime.now().plusDays(7)
            );
            couponFacade.issueCoupon(coupon.id(), "user1234", ZonedDateTime.now());

            // act
            ResponseEntity<ApiResponse<List<CouponDto.Issued.Response>>> response =
                testRestTemplate.exchange(
                    ENDPOINT_ADMIN_COUPONS + "/" + coupon.id() + "/issues",
                    HttpMethod.GET,
                    new HttpEntity<>(adminHeaders()),
                    issuedCouponListResponseType()
                );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data()).hasSize(1),
                () -> assertThat(response.getBody().data().get(0).userLoginId()).isEqualTo("user1234"),
                () -> assertThat(response.getBody().data().get(0).status()).isEqualTo(CouponStatus.AVAILABLE)
            );
        }
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("Admin LDAP 인증")
    @Nested
    class AdminAuthentication {

        @DisplayName("관리자 LDAP 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void throwsUnauthorized_whenAdminLdapHeaderIsMissing() {
            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(
                    ENDPOINT_ADMIN_BRANDS,
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    voidResponseType()
                );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("관리자 LDAP 헤더가 올바르지 않으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void throwsUnauthorized_whenAdminLdapHeaderIsInvalid() {
            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(
                    ENDPOINT_ADMIN_BRANDS,
                    HttpMethod.GET,
                    new HttpEntity<>(adminHeaders("wrong.ldap")),
                    voidResponseType()
                );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("Brand Admin API")
    @Nested
    class BrandAdmin {

        @DisplayName("브랜드를 생성, 조회, 수정, 삭제할 수 있다.")
        @Test
        void managesBrandCrud_whenAdminAuthenticated() {
            // arrange
            BrandDto.Create.V1.Request createRequest = new BrandDto.Create.V1.Request(
                "Loopers",
                "감성 이커머스 브랜드"
            );

            // act
            ResponseEntity<ApiResponse<BrandDto.Create.V1.Response>> createResponse =
                testRestTemplate.exchange(
                    ENDPOINT_ADMIN_BRANDS,
                    HttpMethod.POST,
                    new HttpEntity<>(createRequest, adminHeaders()),
                    brandCreateResponseType()
                );
            Long brandId = createResponse.getBody().data().id();

            ResponseEntity<ApiResponse<BrandDto.Get.V1.Response>> getResponse =
                testRestTemplate.exchange(
                    ENDPOINT_ADMIN_BRANDS + "/" + brandId,
                    HttpMethod.GET,
                    new HttpEntity<>(adminHeaders()),
                    brandGetResponseType()
                );

            BrandDto.Update.V1.Request updateRequest = new BrandDto.Update.V1.Request(
                "Loopers Updated",
                "수정된 브랜드 설명"
            );
            ResponseEntity<ApiResponse<BrandDto.Update.V1.Response>> updateResponse =
                testRestTemplate.exchange(
                    ENDPOINT_ADMIN_BRANDS + "/" + brandId,
                    HttpMethod.PUT,
                    new HttpEntity<>(updateRequest, adminHeaders()),
                    brandUpdateResponseType()
                );

            ResponseEntity<ApiResponse<List<BrandDto.List.V1.Response>>> listResponse =
                testRestTemplate.exchange(
                    ENDPOINT_ADMIN_BRANDS,
                    HttpMethod.GET,
                    new HttpEntity<>(adminHeaders()),
                    brandListResponseType()
                );

            ResponseEntity<ApiResponse<Void>> deleteResponse =
                testRestTemplate.exchange(
                    ENDPOINT_ADMIN_BRANDS + "/" + brandId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(adminHeaders()),
                    voidResponseType()
                );

            // assert
            BrandJpaEntity deletedBrand = brandJpaRepository.findById(brandId).orElseThrow();
            assertAll(
                () -> assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(getResponse.getBody().data().name()).isEqualTo("Loopers"),
                () -> assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(updateResponse.getBody().data().name()).isEqualTo("Loopers Updated"),
                () -> assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(listResponse.getBody().data()).extracting(BrandDto.List.V1.Response::id)
                    .contains(brandId),
                () -> assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(deletedBrand.getDeletedAt()).isNotNull()
            );
        }
    }

    @DisplayName("Product Admin API")
    @Nested
    class ProductAdmin {

        @DisplayName("상품을 조회, 수정, 삭제할 수 있다.")
        @Test
        void managesProductReadUpdateDelete_whenAdminAuthenticated() {
            // arrange
            BrandJpaEntity brand = saveBrand("Loopers", "감성 이커머스 브랜드");
            ProductJpaEntity product = saveProduct(brand.getId(), "니트", "부드러운 니트", 30_000L, 10);

            // act
            ResponseEntity<ApiResponse<List<ProductDto.List.V1.Response>>> listResponse =
                testRestTemplate.exchange(
                    ENDPOINT_ADMIN_PRODUCTS,
                    HttpMethod.GET,
                    new HttpEntity<>(adminHeaders()),
                    productListResponseType()
                );

            ResponseEntity<ApiResponse<ProductDto.Get.V1.Response>> getResponse =
                testRestTemplate.exchange(
                    ENDPOINT_ADMIN_PRODUCTS + "/" + product.getId(),
                    HttpMethod.GET,
                    new HttpEntity<>(adminHeaders()),
                    productGetResponseType()
                );

            ProductDto.Update.V1.Request updateRequest = new ProductDto.Update.V1.Request(
                "니트 Updated",
                "수정된 니트 설명",
                35_000L,
                7
            );
            ResponseEntity<ApiResponse<ProductDto.Update.V1.Response>> updateResponse =
                testRestTemplate.exchange(
                    ENDPOINT_ADMIN_PRODUCTS + "/" + product.getId(),
                    HttpMethod.PUT,
                    new HttpEntity<>(updateRequest, adminHeaders()),
                    productUpdateResponseType()
                );

            ResponseEntity<ApiResponse<Void>> deleteResponse =
                testRestTemplate.exchange(
                    ENDPOINT_ADMIN_PRODUCTS + "/" + product.getId(),
                    HttpMethod.DELETE,
                    new HttpEntity<>(adminHeaders()),
                    voidResponseType()
                );

            // assert
            ProductJpaEntity deletedProduct = productJpaRepository.findById(product.getId()).orElseThrow();
            assertAll(
                () -> assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(listResponse.getBody().data()).extracting(ProductDto.List.V1.Response::id)
                    .contains(product.getId()),
                () -> assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(getResponse.getBody().data().id()).isEqualTo(product.getId()),
                () -> assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(updateResponse.getBody().data().name()).isEqualTo("니트 Updated"),
                () -> assertThat(updateResponse.getBody().data().price()).isEqualTo(35_000L),
                () -> assertThat(updateResponse.getBody().data().stock()).isEqualTo(7),
                () -> assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(deletedProduct.getDeletedAt()).isNotNull()
            );
        }
    }

    @DisplayName("Order Admin API")
    @Nested
    class OrderAdmin {

        @DisplayName("전체 주문 목록과 주문 상세를 조회할 수 있다.")
        @Test
        void returnsOrdersAndOrderDetail_whenAdminAuthenticated() {
            // arrange
            OrderJpaEntity order = saveOrder("user1234", 1L, "니트", 30_000L, 2);

            // act
            ResponseEntity<ApiResponse<List<OrderDto.List.V1.Response>>> listResponse =
                testRestTemplate.exchange(
                    ENDPOINT_ADMIN_ORDERS,
                    HttpMethod.GET,
                    new HttpEntity<>(adminHeaders()),
                    orderListResponseType()
                );

            ResponseEntity<ApiResponse<OrderDto.Get.V1.Response>> getResponse =
                testRestTemplate.exchange(
                    ENDPOINT_ADMIN_ORDERS + "/" + order.getId(),
                    HttpMethod.GET,
                    new HttpEntity<>(adminHeaders()),
                    orderGetResponseType()
                );

            // assert
            assertAll(
                () -> assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(listResponse.getBody().data()).extracting(OrderDto.List.V1.Response::id)
                    .contains(order.getId()),
                () -> assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(getResponse.getBody().data().id()).isEqualTo(order.getId()),
                () -> assertThat(getResponse.getBody().data().userLoginId()).isEqualTo("user1234"),
                () -> assertThat(getResponse.getBody().data().orderLines()).hasSize(1),
                () -> assertThat(getResponse.getBody().data().totalAmount()).isEqualTo(60_000L)
            );
        }
    }

    private BrandJpaEntity saveBrand(String name, String description) {
        return brandJpaRepository.save(BrandJpaEntity.from(new Brand(name, description)));
    }

    private ProductJpaEntity saveProduct(Long brandId, String name, String description, Long price, Integer stock) {
        return productJpaRepository.save(ProductJpaEntity.from(new Product(brandId, name, description, price, stock)));
    }

    private OrderJpaEntity saveOrder(
        String userLoginId,
        Long productId,
        String productName,
        Long price,
        Integer quantity
    ) {
        Order order = new Order(
            userLoginId,
            List.of(new OrderLine(productId, productName, price, quantity))
        );
        return orderJpaRepository.save(OrderJpaEntity.from(order));
    }

    private HttpHeaders adminHeaders() {
        return adminHeaders("loopers.admin");
    }

    private HttpHeaders adminHeaders(String ldap) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", ldap);
        return headers;
    }

    private ParameterizedTypeReference<ApiResponse<BrandDto.Create.V1.Response>> brandCreateResponseType() {
        return new ParameterizedTypeReference<>() {};
    }

    private ParameterizedTypeReference<ApiResponse<BrandDto.Get.V1.Response>> brandGetResponseType() {
        return new ParameterizedTypeReference<>() {};
    }

    private ParameterizedTypeReference<ApiResponse<List<BrandDto.List.V1.Response>>> brandListResponseType() {
        return new ParameterizedTypeReference<>() {};
    }

    private ParameterizedTypeReference<ApiResponse<BrandDto.Update.V1.Response>> brandUpdateResponseType() {
        return new ParameterizedTypeReference<>() {};
    }

    private ParameterizedTypeReference<ApiResponse<ProductDto.Get.V1.Response>> productGetResponseType() {
        return new ParameterizedTypeReference<>() {};
    }

    private ParameterizedTypeReference<ApiResponse<List<ProductDto.List.V1.Response>>> productListResponseType() {
        return new ParameterizedTypeReference<>() {};
    }

    private ParameterizedTypeReference<ApiResponse<ProductDto.Update.V1.Response>> productUpdateResponseType() {
        return new ParameterizedTypeReference<>() {};
    }

    private ParameterizedTypeReference<ApiResponse<OrderDto.Get.V1.Response>> orderGetResponseType() {
        return new ParameterizedTypeReference<>() {};
    }

    private ParameterizedTypeReference<ApiResponse<List<OrderDto.List.V1.Response>>> orderListResponseType() {
        return new ParameterizedTypeReference<>() {};
    }

    private ParameterizedTypeReference<ApiResponse<CouponDto.Create.V1.Response>> couponCreateResponseType() {
        return new ParameterizedTypeReference<>() {};
    }

    private ParameterizedTypeReference<ApiResponse<CouponDto.Template.Response>> couponGetResponseType() {
        return new ParameterizedTypeReference<>() {};
    }

    private ParameterizedTypeReference<ApiResponse<List<CouponDto.Template.Response>>> couponListResponseType() {
        return new ParameterizedTypeReference<>() {};
    }

    private ParameterizedTypeReference<ApiResponse<CouponDto.Update.V1.Response>> couponUpdateResponseType() {
        return new ParameterizedTypeReference<>() {};
    }

    private ParameterizedTypeReference<ApiResponse<List<CouponDto.Issued.Response>>> issuedCouponListResponseType() {
        return new ParameterizedTypeReference<>() {};
    }

    private ParameterizedTypeReference<ApiResponse<Void>> voidResponseType() {
        return new ParameterizedTypeReference<>() {};
    }
}
